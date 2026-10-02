package com.memespeak.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memespeak.dto.TranslationResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis-backed translation cache with stampede protection.
 *
 * <p><strong>Cache key structure:</strong>
 * <pre>
 *   meme:translation:v1:{sha256(normalizedInput)}
 * </pre>
 * The version prefix ({@code v1}) allows complete cache invalidation
 * by changing the prefix when the prompt or response contract changes.
 *
 * <p><strong>Cache stampede protection:</strong>
 * When many concurrent requests arrive for the same uncached input,
 * only one thread calls the LLM. Others wait briefly for the result
 * to appear in cache. This uses a Redis SETNX lock pattern:
 * <ol>
 *   <li>Try to get from cache → HIT: return immediately.</li>
 *   <li>Try to acquire a Redis lock (SETNX with TTL).</li>
 *   <li>If lock acquired: call supplier (LLM), store in cache, release lock.</li>
 *   <li>If lock not acquired (another thread has it): poll cache until the
 *       result appears, then return it. Falls back to a direct LLM call
 *       if the lock holder fails.</li>
 * </ol>
 *
 * <p><strong>Safety invariant:</strong>
 * This service is called AFTER the safety guardrail passes.
 * Unsafe/blocked requests are never cached.
 * Failed LLM responses are never cached as successful results.
 */
@Slf4j
@Service
public class TranslationCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${memespeak.cache.ttl-hours:24}")
    private long ttlHours;

    @Value("${memespeak.cache.key-prefix:meme:translation:v1:}")
    private String keyPrefix;

    @Value("${memespeak.cache.lock-prefix:meme:lock:v1:}")
    private String lockPrefix;

    @Value("${memespeak.cache.lock-timeout-seconds:30}")
    private long lockTimeoutSeconds;

    @Value("${memespeak.cache.poll-interval-ms:300}")
    private long pollIntervalMs;

    @Value("${memespeak.cache.poll-max-attempts:15}")
    private int pollMaxAttempts;

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public TranslationCacheService(StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper,
                                    MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheHitCounter = Counter.builder("memespeak.cache.hits")
                .description("Translation cache hits")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("memespeak.cache.misses")
                .description("Translation cache misses")
                .register(meterRegistry);
    }

    /**
     * Returns a cached translation or calls {@code llmSupplier} to produce one.
     *
     * <p>The supplier is only called once for concurrent identical requests.
     *
     * @param cacheKey the pre-computed cache key (hash of normalized input)
     * @param llmSupplier invoked at most once per cache miss to produce a translation
     * @return the translation, either from cache or freshly produced
     */
    public TranslationResponse getOrCompute(String cacheKey, Supplier<TranslationResponse> llmSupplier) {
        String redisKey = keyPrefix + cacheKey;
        String lockKey  = lockPrefix + cacheKey;

        // --- Cache HIT ---
        Optional<TranslationResponse> cached = getFromCache(redisKey);
        if (cached.isPresent()) {
            cacheHitCounter.increment();
            log.debug("Cache HIT for key={}", cacheKey);
            return cached.get();
        }

        cacheMissCounter.increment();
        log.debug("Cache MISS for key={}", cacheKey);

        // --- Try to acquire the stampede-protection lock ---
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(lockTimeoutSeconds));

        if (Boolean.TRUE.equals(lockAcquired)) {
            // This thread owns the lock — call the LLM
            try {
                // Double-check: another node may have populated the cache while we competed
                Optional<TranslationResponse> doubleCheck = getFromCache(redisKey);
                if (doubleCheck.isPresent()) {
                    cacheHitCounter.increment();
                    return doubleCheck.get();
                }

                TranslationResponse result = llmSupplier.get();
                storeInCache(redisKey, result);
                return result;
            } finally {
                redisTemplate.delete(lockKey);
            }
        }

        // --- Another thread holds the lock — poll for the result ---
        log.debug("Lock held by another thread, polling cache for key={}", cacheKey);
        for (int attempt = 0; attempt < pollMaxAttempts; attempt++) {
            try {
                TimeUnit.MILLISECONDS.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            Optional<TranslationResponse> polled = getFromCache(redisKey);
            if (polled.isPresent()) {
                cacheHitCounter.increment();
                log.debug("Cache populated by peer thread after {} polls", attempt + 1);
                return polled.get();
            }
        }

        // --- Fallback: peer thread may have failed — call LLM directly ---
        log.warn("Cache not populated after {} polls, calling LLM directly as fallback", pollMaxAttempts);
        TranslationResponse result = llmSupplier.get();
        storeInCache(redisKey, result);
        return result;
    }

    private Optional<TranslationResponse> getFromCache(String redisKey) {
        try {
            String json = redisTemplate.opsForValue().get(redisKey);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, TranslationResponse.class));
        } catch (Exception e) {
            log.warn("Cache read error for key {}: {}", redisKey, e.getMessage());
            return Optional.empty();
        }
    }

    private void storeInCache(String redisKey, TranslationResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey, json, Duration.ofHours(ttlHours));
            log.debug("Cached translation at key={} TTL={}h", redisKey, ttlHours);
        } catch (Exception e) {
            // Cache failures are non-fatal — the response was already computed
            log.warn("Failed to cache translation: {}", e.getMessage());
        }
    }
}
