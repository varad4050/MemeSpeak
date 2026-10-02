package com.memespeak.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration.
 *
 * <p>We use {@link StringRedisTemplate} throughout the application.
 * JSON serialization/deserialization is handled by Jackson in each service,
 * giving us full control over the serialization format and error handling.
 *
 * <p>The Redis connection is configured via {@code spring.data.redis.*}
 * properties in application.yml — no additional programmatic config needed
 * for the Lettuce client.
 */
@Configuration
public class RedisConfig {

    /**
     * Provides a {@link StringRedisTemplate} bean.
     *
     * <p>Spring Boot auto-configures this bean if one is not explicitly declared,
     * but we declare it explicitly for clarity and to allow future customization
     * (e.g., connection pooling, SSL, Sentinel).
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
