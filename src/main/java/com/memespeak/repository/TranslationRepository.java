package com.memespeak.repository;

import com.memespeak.entity.Translation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, UUID> {

    /** Returns a paginated translation history for a given user. */
    Page<Translation> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
