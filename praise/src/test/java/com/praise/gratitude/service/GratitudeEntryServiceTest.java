package com.praise.gratitude.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.praise.auth.security.AuthenticatedUser;
import com.praise.auth.security.CurrentUser;
import com.praise.gratitude.domain.GratitudeEntry;
import com.praise.gratitude.persistence.GratitudeEntryRepository;
import com.praise.gratitude.web.GratitudeNotFoundException;
import com.praise.gratitude.web.GratitudeRequest;
import com.praise.gratitude.web.GratitudeResponse;
import com.praise.user.domain.UserAccount;
import com.praise.user.persistence.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GratitudeEntryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");

    @Mock
    private GratitudeEntryRepository entries;

    @Mock
    private UserAccountRepository users;

    private GratitudeEntryService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = UserAccount.create("ada", "hash", NOW);
        ReflectionTestUtils.setField(user, "id", 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedUser(1L), null, List.of()));
        service = new GratitudeEntryService(entries, users, new CurrentUser(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUsesTheSignedInUserAndDefaultsTheDate() {
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(entries.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        GratitudeResponse response = service.create(new GratitudeRequest(null, "  Hello  ", "  Warm sun  ", "   "));

        ArgumentCaptor<GratitudeEntry> saved = ArgumentCaptor.forClass(GratitudeEntry.class);
        verify(entries).save(saved.capture());
        assertEquals(user, saved.getValue().getUser());
        assertEquals(LocalDate.of(2026, 10, 28), saved.getValue().getEntryDate());
        assertEquals("Hello", saved.getValue().getTitle());
        assertEquals("Warm sun", saved.getValue().getContent());
        assertNull(saved.getValue().getMoodTag());
        assertEquals(NOW, saved.getValue().getCreatedAt());
        assertEquals(10L, response.id());
    }

    @Test
    void updateDoesNotChangeCreatedAt() {
        Instant original = Instant.parse("2026-01-01T00:00:00Z");
        GratitudeEntry entry = withId(GratitudeEntry.create(user, LocalDate.of(2026, 1, 1), "Old", "Body", "calm", original), 4L);
        when(entries.findByIdAndUserId(4L, 1L)).thenReturn(Optional.of(entry));

        GratitudeResponse response = service.update(4L, new GratitudeRequest(LocalDate.of(2026, 2, 2), "New", "Updated", "glad"));

        assertEquals(original, response.createdAt());
        assertEquals("New", response.title());
        assertEquals(LocalDate.of(2026, 2, 2), response.entryDate());
    }

    @Test
    void missingEntryForThisUserIsNotFound() {
        when(entries.findByIdAndUserId(9L, 1L)).thenReturn(Optional.empty());

        assertThrows(GratitudeNotFoundException.class, () -> service.get(9L));
        verify(entries).findByIdAndUserId(9L, 1L);
    }

    @Test
    void randomOnAnEmptyJournalIsEmpty() {
        when(entries.countByUserId(1L)).thenReturn(0L);

        assertTrue(service.findRandom().isEmpty());
        verify(entries, never()).findByUserId(any(), any());
    }

    @Test
    void randomReturnsOneOfTheSignedInUsersEntries() {
        GratitudeEntry entry = withId(GratitudeEntry.create(user, LocalDate.of(2026, 10, 28), "One", "Body", null, NOW), 3L);
        when(entries.countByUserId(1L)).thenReturn(2L);
        when(entries.findByUserId(org.mockito.ArgumentMatchers.eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        Optional<GratitudeResponse> response = service.findRandom();

        assertTrue(response.isPresent());
        assertEquals(3L, response.get().id());
    }

    @Test
    void deleteRemovesOnlyTheOwnedEntry() {
        GratitudeEntry entry = withId(GratitudeEntry.create(user, LocalDate.of(2026, 10, 28), "One", "Body", null, NOW), 3L);
        when(entries.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(entry));

        service.delete(3L);

        verify(entries).delete(entry);
    }

    private GratitudeEntry withId(GratitudeEntry entry, Long id) {
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }
}
