package com.praise.user.service;

import com.praise.auth.jwt.JwtService;
import com.praise.auth.security.CurrentUser;
import com.praise.auth.web.AuthResponse;
import com.praise.auth.web.InvalidCredentialsException;
import com.praise.auth.web.UserResponse;
import com.praise.auth.web.UsernameAlreadyTakenException;
import com.praise.user.domain.UserAccount;
import com.praise.user.persistence.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);
    private static final String INVALID_LOGIN = "Invalid username or password";

    private final UserAccountRepository users;
    private final JwtService jwtService;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public UserAccountService(
            UserAccountRepository users,
            JwtService jwtService,
            CurrentUser currentUser,
            Clock clock,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.clock = clock;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("unused-timing-placeholder");
    }

    @Transactional
    public AuthResponse register(String username, String rawPassword) {
        String normalized = normalize(username);
        if (users.findByUsername(normalized).isPresent()) {
            throw new UsernameAlreadyTakenException("This username is already taken");
        }
        try {
            UserAccount saved = users.save(UserAccount.create(
                    normalized,
                    passwordEncoder.encode(rawPassword),
                    clock.instant()));
            log.info("Created user {}", saved.getId());
            return tokenFor(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new UsernameAlreadyTakenException("This username is already taken");
        }
    }

    public AuthResponse login(String username, String rawPassword) {
        UserAccount user = users.findByUsername(normalize(username)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(rawPassword, dummyHash);
            throw new InvalidCredentialsException(INVALID_LOGIN);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException(INVALID_LOGIN);
        }
        log.info("Signed in user {}", user.getId());
        return tokenFor(user);
    }

    public UserResponse current() {
        return users.findById(currentUser.id())
                .map(UserResponse::from)
                .orElseThrow(() -> new IllegalStateException("Authenticated user was not found"));
    }

    private AuthResponse tokenFor(UserAccount user) {
        Instant expiresAt = clock.instant().plus(jwtService.expiration());
        String token = jwtService.issue(user.getId(), expiresAt);
        return new AuthResponse(token, "Bearer", expiresAt, UserResponse.from(user));
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
