package com.memespeak.repository;

import com.memespeak.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Looks up a user by their Google subject ID (JWT {@code sub} claim).
     * This is the primary lookup for every authenticated request.
     */
    Optional<User> findByGoogleId(String googleId);
}
