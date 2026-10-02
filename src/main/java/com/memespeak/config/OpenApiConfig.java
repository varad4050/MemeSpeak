package com.memespeak.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 / Swagger configuration.
 *
 * <p>The Swagger UI is available at: {@code /swagger-ui.html}
 * The OpenAPI JSON spec is at:      {@code /v3/api-docs}
 *
 * <p>The Bearer Authentication scheme is registered globally so that
 * the Swagger UI provides a lock icon on each endpoint, allowing testers
 * to paste their Google ID Token and test authenticated endpoints directly.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MemeSpeak API")
                        .version("1.0.0")
                        .description("""
                                AI-powered internet slang and Gen-Z language translator.
                                
                                **Authentication:**
                                Obtain a Google ID Token and send it as:
                                ```
                                Authorization: Bearer <google-id-token>
                                ```
                                
                                **How to get a Google ID Token for testing:**
                                1. Create a Google OAuth2 app in Google Cloud Console.
                                2. Use the OAuth2 Playground (https://developers.google.com/oauthplayground)
                                   or Postman's OAuth2 flow to get an ID Token.
                                3. Paste the token in the Authorize dialog below.
                                """)
                        .contact(new Contact().name("MemeSpeak Team")))

                // Register the JWT bearer security scheme
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .name("Bearer Authentication")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Google ID Token obtained from OAuth2 flow")));
    }
}
