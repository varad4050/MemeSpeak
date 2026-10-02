package com.memespeak;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Spring ApplicationContext smoke test.
 *
 * <p>Starts a real PostgreSQL and Redis via Testcontainers to verify the
 * entire context wires correctly. The LLM ({@link ChatModel}) is mocked
 * so no real API call is made. The JWT decoder is mocked so no network
 * call to Google is needed.
 *
 * <p>If this test fails, the application cannot start — diagnose this before
 * investigating any other test failures.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class MemeSpeakApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host",     redis::getHost);
        registry.add("spring.data.redis.port",     () -> redis.getMappedPort(6379));
    }

    // Mock the LLM — we don't want to call real OpenAI in context tests
    @MockBean
    ChatModel chatModel;

    // Mock the JWT decoder — we don't want to call Google's JWKS endpoint
    @MockBean
    JwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
        // If the ApplicationContext fails to start, this test fails with
        // a descriptive Spring error — not a null pointer or cryptic assertion.
    }
}
