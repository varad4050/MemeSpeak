package com.memespeak.service;

import com.memespeak.ai.LlmTranslationService;
import com.memespeak.cache.TranslationCacheService;
import com.memespeak.dto.TranslationRequest;
import com.memespeak.dto.TranslationResponse;
import com.memespeak.entity.Translation;
import com.memespeak.entity.User;
import com.memespeak.exception.RateLimitException;
import com.memespeak.exception.SafetyException;
import com.memespeak.guardrail.abuse.AbuseDetectionService;
import com.memespeak.guardrail.abuse.SafetyDecision;
import com.memespeak.guardrail.injection.PromptInjectionGuard;
import com.memespeak.guardrail.pii.PiiRedactionService;
import com.memespeak.repository.TranslationRepository;
import com.memespeak.util.TextNormalizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Orchestrates the complete translation pipeline.
 *
 * <p>Pipeline (in order):
 * <pre>
 *   1. Rate limit check (Redis counter per user per minute)
 *   2. Prompt injection check
 *   3. Abuse / profanity check (on normalized text)
 *   4. PII redaction (on cache-normalized text)
 *   5. Cache lookup
 *   6. LLM call (on cache miss only)
 *   7. Cache storage (on LLM success only)
 *   8. Translation persisted to DB
 *   9. Response returned
 * </pre>
 *
 * <p>Safety invariants:
 * <ul>
 *   <li>Rate limited requests never reach the LLM.</li>
 *   <li>Unsafe/blocked requests never reach the LLM and are never cached.</li>
 *   <li>Failed LLM responses are never cached.</li>
 *   <li>Raw user PII is never sent to the LLM.</li>
 * </ul>
 */
@Slf4j
@Service
public class TranslationService {

    private final AbuseDetectionService abuseDetector;
    private final PromptInjectionGuard injectionGuard;
    private final PiiRedactionService piiRedactor;
    private final TextNormalizer normalizer;
    private final TranslationCacheService cacheService;
    private final LlmTranslationService llmService;
    private final UserService userService;
    private final TranslationRepository translationRepository;
    private final StringRedisTemplate redisTemplate;

    private final Counter totalCounter;
    private final Counter blockedCounter;
    private final Counter rateLimitCounter;
    private final Timer latencyTimer;

    @Value("${memespeak.rate-limit.requests-per-minute:30}")
    private long requestsPerMinute;

    @Value("${memespeak.rate-limit.key-prefix:meme:rate:}")
    private String rateLimitKeyPrefix;

    public TranslationService(AbuseDetectionService abuseDetector,
                               PromptInjectionGuard injectionGuard,
                               PiiRedactionService piiRedactor,
                               TextNormalizer normalizer,
                               TranslationCacheService cacheService,
                               LlmTranslationService llmService,
                               UserService userService,
                               TranslationRepository translationRepository,
                               StringRedisTemplate redisTemplate,
                               MeterRegistry meterRegistry) {
        this.abuseDetector = abuseDetector;
        this.injectionGuard = injectionGuard;
        this.piiRedactor = piiRedactor;
        this.normalizer = normalizer;
        this.cacheService = cacheService;
        this.llmService = llmService;
        this.userService = userService;
        this.translationRepository = translationRepository;
        this.redisTemplate = redisTemplate;

        this.totalCounter = Counter.builder("memespeak.translations.total")
                .description("Total translation requests received").register(meterRegistry);
        this.blockedCounter = Counter.builder("memespeak.translations.blocked")
                .description("Translation requests blocked by safety guardrail").register(meterRegistry);
        this.rateLimitCounter = Counter.builder("memespeak.ratelimit.violations")
                .description("Rate limit violations").register(meterRegistry);
        this.latencyTimer = Timer.builder("memespeak.translations.latency")
                .description("End-to-end translation request latency").register(meterRegistry);
    }

    /**
     * Executes the full translation pipeline for an authenticated user.
     *
     * @param request the validated translation request
     * @param jwt     the validated Google ID token for the requesting user
     * @return the translation result
     * @throws RateLimitException if the user exceeds their request quota
     * @throws SafetyException    if the input violates abuse or injection policy
     */
    public TranslationResponse translate(TranslationRequest request, Jwt jwt) {
        return latencyTimer.record(() -> executeTranslation(request, jwt));
    }

    private TranslationResponse executeTranslation(TranslationRequest request, Jwt jwt) {
        totalCounter.increment();

        // ------------------------------------------------------------------ 1. Rate limit
        String userId = jwt.getSubject();
        enforceRateLimit(userId);

        String rawText = request.text();

        // ------------------------------------------------------------------ 2. Injection check
        if (injectionGuard.isInjectionAttempt(rawText)) {
            blockedCounter.increment();
            log.info("Prompt injection attempt detected for user={}", userId);
            throw new SafetyException("PROMPT_INJECTION");
        }

        // ------------------------------------------------------------------ 3. Abuse check
        SafetyDecision decision = abuseDetector.evaluate(rawText);
        if (decision.isBlocked()) {
            blockedCounter.increment();
            log.info("Abuse detection blocked request: reason={}", decision.reason());
            throw new SafetyException(decision.reason());
        }

        // ------------------------------------------------------------------ 4. PII redaction + normalization
        String normalizedForCache = normalizer.normalizeForCache(rawText);
        String sanitizedForLlm   = piiRedactor.redact(normalizedForCache);
        String cacheKey           = sha256(normalizedForCache);

        // ------------------------------------------------------------------ 5-7. Cache + LLM
        TranslationResponse response = cacheService.getOrCompute(
                cacheKey,
                () -> llmService.translate(sanitizedForLlm)
        );

        // ------------------------------------------------------------------ 8. Persist (async would be ideal — sync is fine for V1)
        persistTranslation(jwt, cacheKey, response);

        return response;
    }

    // -------------------------------------------------------------------------
    // Rate limiting — fixed window per user per minute using Redis INCR
    // -------------------------------------------------------------------------

    private void enforceRateLimit(String userId) {
        long minuteBucket = System.currentTimeMillis() / 60_000;
        String key = rateLimitKeyPrefix + userId + ":" + minuteBucket;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            log.warn("Redis INCR returned null for rate limit key={}, allowing request", key);
            return;
        }
        if (count == 1) {
            // First request in this window — set expiry
            redisTemplate.expire(key, Duration.ofMinutes(2));
        }
        if (count > requestsPerMinute) {
            rateLimitCounter.increment();
            log.info("Rate limit exceeded: userId={} count={}", userId, count);
            throw new RateLimitException();
        }
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private void persistTranslation(Jwt jwt, String inputHash, TranslationResponse response) {
        try {
            User user = userService.findOrCreate(jwt);
            Translation record = new Translation(user, inputHash, response.meaning(), response.explanation());
            translationRepository.save(record);
        } catch (Exception e) {
            // Persistence failure is non-fatal — we still return the translation result.
            // The error is logged for investigation.
            log.error("Failed to persist translation record: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec — this cannot happen
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
