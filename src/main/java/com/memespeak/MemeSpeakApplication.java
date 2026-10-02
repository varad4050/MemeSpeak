package com.memespeak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MemeSpeak Application Entry Point.
 *
 * <p>This class is intentionally minimal. Spring Boot's component scanning,
 * auto-configuration, and context initialization are triggered by
 * {@code @SpringBootApplication}, which is a composed annotation that includes:
 * <ul>
 *   <li>{@code @Configuration}       — marks this as a config class</li>
 *   <li>{@code @EnableAutoConfiguration} — activates Spring Boot auto-config</li>
 *   <li>{@code @ComponentScan}        — scans the {@code com.memespeak} package</li>
 * </ul>
 *
 * <p>Do not add business logic, bean definitions, or configuration here.
 * Use dedicated {@code @Configuration} classes inside the {@code config} package.
 */
@SpringBootApplication
public class MemeSpeakApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemeSpeakApplication.class, args);
    }
}
