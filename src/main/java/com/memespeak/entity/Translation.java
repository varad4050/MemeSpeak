package com.memespeak.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted record of a translation result.
 *
 * <p>Data minimization: we do NOT store the raw user input.
 * Instead we store:
 * <ul>
 *   <li>{@code inputHash} — SHA-256 hex of the cache-normalized input,
 *       useful for analytics and deduplication without storing PII.</li>
 *   <li>{@code meaning} and {@code explanation} — the translation output,
 *       which contains no user-supplied personal data.</li>
 * </ul>
 *
 * <p>A translation belongs to exactly one user. Deleting the user
 * cascades to their translations.
 */
@Entity
@Table(
        name = "translations",
        indexes = {
                @Index(name = "idx_translations_user_id", columnList = "user_id"),
                @Index(name = "idx_translations_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Translation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * SHA-256 hex digest of the cache-normalized input text.
     * Allows correlation with cache entries and deduplication analytics
     * without storing the raw user text.
     */
    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "meaning", nullable = false, columnDefinition = "TEXT")
    private String meaning;

    @Column(name = "explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Translation(User user, String inputHash, String meaning, String explanation) {
        this.user = user;
        this.inputHash = inputHash;
        this.meaning = meaning;
        this.explanation = explanation;
    }
}
