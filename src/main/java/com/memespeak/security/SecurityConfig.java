package com.memespeak.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Spring Security configuration for a stateless JWT REST API.
 *
 * <p>Authentication model:
 * <ul>
 *   <li>The client (browser, mobile, Postman) obtains a Google ID Token
 *       by completing the OAuth2 flow directly with Google.</li>
 *   <li>The token is sent in every request as {@code Authorization: Bearer <token>}.</li>
 *   <li>Spring Security validates the JWT signature against Google's JWKS
 *       (fetched automatically from the configured issuer URI).</li>
 *   <li>No sessions are created — the API is fully stateless.</li>
 * </ul>
 *
 * <p>Public endpoints (no token required):
 * <ul>
 *   <li>{@code /actuator/health} — liveness probe</li>
 *   <li>{@code /actuator/info}   — application info</li>
 *   <li>{@code /v3/api-docs/**} — OpenAPI spec</li>
 *   <li>{@code /swagger-ui/**}  — Swagger UI</li>
 *   <li>{@code /swagger-ui.html}</li>
 * </ul>
 *
 * <p>All other endpoints require a valid Google ID Token.
 *
 * <p>CSRF is disabled because this is a stateless API — there are no cookies
 * or server-side sessions that CSRF attacks could exploit.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String GOOGLE_ISSUER_URI = "https://accounts.google.com";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless REST API — no sessions, no cookies
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF is irrelevant for a stateless JWT API
            .csrf(AbstractHttpConfigurer::disable)

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                    // Infrastructure and documentation — publicly accessible
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    // Everything else requires a valid Google JWT
                    .anyRequest().authenticated()
            )

            // OAuth2 Resource Server — validate Bearer JWTs
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.decoder(jwtDecoder()))
                    // Return 401 when no/invalid token is provided (not Spring's default 403)
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            );

        return http.build();
    }

    /**
     * Configures the JWT decoder to validate Google ID Tokens.
     *
     * <p>Spring Security will:
     * <ol>
     *   <li>Fetch Google's OpenID Connect discovery document.</li>
     *   <li>Download Google's public JWKS.</li>
     *   <li>Validate every incoming JWT's signature, issuer, and expiry.</li>
     * </ol>
     *
     * <p>The JWKS is cached automatically — there is no performance concern
     * from network calls on every request.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation(GOOGLE_ISSUER_URI);
    }
}
