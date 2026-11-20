package com.praise.auth.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.expiration}") Duration expiration,
            Clock clock) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.clock = clock;
    }

    public Duration expiration() {
        return expiration;
    }

    public String issue(Long userId, Instant expiresAt) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public Optional<Long> parseUserId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
