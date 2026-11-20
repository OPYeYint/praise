package com.praise.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void issuedTokenParsesBackToTheUserId() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-28T12:00:00Z"));
        JwtService jwtService = new JwtService("test-jwt-secret-must-be-32-bytes-min", Duration.ofHours(12), clock);

        String token = jwtService.issue(42L, clock.instant().plus(Duration.ofHours(12)));

        assertEquals(Optional.of(42L), jwtService.parseUserId(token));
    }

    @Test
    void expiredTokenIsRejected() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-28T12:00:00Z"));
        JwtService jwtService = new JwtService("test-jwt-secret-must-be-32-bytes-min", Duration.ofHours(12), clock);
        String token = jwtService.issue(42L, clock.instant().plus(Duration.ofHours(12)));

        clock.plus(Duration.ofHours(13));

        assertEquals(Optional.empty(), jwtService.parseUserId(token));
        assertTrue(jwtService.parseUserId("not-a-token").isEmpty());
    }

    private static final class MutableClock extends java.time.Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void plus(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
