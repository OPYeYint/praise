package com.praise.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.praise.auth.jwt.JwtService;
import com.praise.auth.security.AuthenticatedUser;
import com.praise.auth.security.CurrentUser;
import com.praise.auth.web.AuthResponse;
import com.praise.auth.web.InvalidCredentialsException;
import com.praise.auth.web.UsernameAlreadyTakenException;
import com.praise.user.domain.UserAccount;
import com.praise.user.persistence.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    @Mock
    private UserAccountRepository users;

    @Mock
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserAccountService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new UserAccountService(users, jwtService, new CurrentUser(), clock, passwordEncoder);
        lenient().when(jwtService.expiration()).thenReturn(Duration.ofHours(12));
        lenient().when(jwtService.issue(any(), any())).thenReturn("token");
    }

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerStoresAPasswordHashAndReturnsAToken() {
        when(users.findByUsername("ada")).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 8L));

        AuthResponse response = service.register("Ada", "password123");

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertEquals("ada", saved.getValue().getUsername());
        assertNotEquals("password123", saved.getValue().getPasswordHash());
        assertTrue(passwordEncoder.matches("password123", saved.getValue().getPasswordHash()));
        assertEquals(8L, response.user().id());
        assertEquals("ada", response.user().username());
        assertEquals("Bearer", response.tokenType());
        verify(jwtService).issue(eq(8L), eq(NOW.plus(Duration.ofHours(12))));
    }

    @Test
    void duplicateUsernameIsRejected() {
        when(users.findByUsername("ada")).thenReturn(Optional.of(withId(UserAccount.create("ada", "hash", NOW), 5L)));

        assertThrows(UsernameAlreadyTakenException.class, () -> service.register("ada", "password123"));
        verify(users, never()).save(any());
    }

    @Test
    void loginAcceptsTheRightPassword() {
        UserAccount existing = withId(UserAccount.create("ada", passwordEncoder.encode("password123"), NOW), 5L);
        when(users.findByUsername("ada")).thenReturn(Optional.of(existing));

        AuthResponse response = service.login("Ada", "password123");

        assertEquals(5L, response.user().id());
        assertEquals("ada", response.user().username());
    }

    @Test
    void wrongPasswordAndUnknownUsernameUseTheSameError() {
        when(users.findByUsername("ada")).thenReturn(Optional.of(
                withId(UserAccount.create("ada", passwordEncoder.encode("password123"), NOW), 5L)));
        when(users.findByUsername("missing")).thenReturn(Optional.empty());

        InvalidCredentialsException wrongPassword = assertThrows(
                InvalidCredentialsException.class, () -> service.login("ada", "not-the-password"));
        InvalidCredentialsException unknownUser = assertThrows(
                InvalidCredentialsException.class, () -> service.login("missing", "password123"));

        assertEquals(wrongPassword.getMessage(), unknownUser.getMessage());
    }

    @Test
    void currentUserComesFromTheToken() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedUser(5L), null, java.util.List.of()));
        when(users.findById(5L)).thenReturn(Optional.of(withId(UserAccount.create("ada", "hash", NOW), 5L)));

        assertEquals("ada", service.current().username());
    }

    private UserAccount withId(UserAccount user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
