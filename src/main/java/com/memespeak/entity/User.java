package com.memespeak.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an authenticated MemeSpeak user.
 *
 * <p>Users are created automatically on first Google sign-in.
 * We do NOT store any Google credentials — only the Google subject ID
 * ({@code sub} claim) which is a stable, non-secret identifier.
 *
 * <p>Data minimization: we store only what we actually use.
 */
@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_google_id", columnList = "google_id", unique = true),
                @Index(name = "idx_users_email", columnList = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Google's stable, unique identifier for this user (the JWT {@code sub} claim).
     * Never changes, even if the user changes their email or name.
     */
    @Column(name = "google_id", nullable = false, unique = true, length = 255)
    private String googleId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login", nullable = false)
    private Instant lastLogin;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.lastLogin = Instant.now();
    }

    public User(String googleId, String email, String name) {
        this.googleId = googleId;
        this.email = email;
        this.name = name;
    }
}
