package com.memespeak.service;

import com.memespeak.entity.User;
import com.memespeak.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Manages user lifecycle: find-or-create on first login, update last login.
 *
 * <p>Users are identified by their Google subject ID (JWT {@code sub} claim).
 * This ID is stable, non-secret, and never changes even if the user
 * changes their Google account email or display name.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Returns the User for the given Google JWT, creating one if this is
     * their first sign-in.
     *
     * <p>This method is transactional to ensure the find-or-create is atomic.
     *
     * @param jwt a validated Google ID token parsed by Spring Security
     * @return the persisted User entity
     */
    @Transactional
    public User findOrCreate(Jwt jwt) {
        String googleId = jwt.getSubject();
        String email    = jwt.getClaimAsString("email");
        String name     = jwt.getClaimAsString("name");

        return userRepository.findByGoogleId(googleId)
                .map(user -> updateLastLogin(user))
                .orElseGet(() -> {
                    log.info("New user — creating profile for googleId={}", googleId);
                    User newUser = new User(googleId, email, name);
                    return userRepository.save(newUser);
                });
    }

    private User updateLastLogin(User user) {
        user.setLastLogin(Instant.now());
        return userRepository.save(user);
    }
}
