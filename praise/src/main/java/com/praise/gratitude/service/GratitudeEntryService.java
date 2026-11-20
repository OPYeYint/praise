package com.praise.gratitude.service;

import com.praise.auth.security.CurrentUser;
import com.praise.gratitude.domain.GratitudeEntry;
import com.praise.gratitude.persistence.GratitudeEntryRepository;
import com.praise.gratitude.web.GratitudeNotFoundException;
import com.praise.gratitude.web.GratitudeRequest;
import com.praise.gratitude.web.GratitudeResponse;
import com.praise.user.domain.UserAccount;
import com.praise.user.persistence.UserAccountRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GratitudeEntryService {

    private static final Logger log = LoggerFactory.getLogger(GratitudeEntryService.class);

    private final GratitudeEntryRepository entries;
    private final UserAccountRepository users;
    private final CurrentUser currentUser;
    private final Clock clock;

    public GratitudeEntryService(
            GratitudeEntryRepository entries,
            UserAccountRepository users,
            CurrentUser currentUser,
            Clock clock) {
        this.entries = entries;
        this.users = users;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public GratitudeResponse create(GratitudeRequest request) {
        UserAccount user = currentAccount();
        GratitudeEntry entry = GratitudeEntry.create(
                user,
                resolveDate(request.entryDate()),
                request.title(),
                request.content(),
                request.moodTag(),
                clock.instant());
        GratitudeEntry saved = entries.save(entry);
        log.info("Created gratitude entry {} for user {}", saved.getId(), user.getId());
        return GratitudeMapper.toResponse(saved);
    }

    public List<GratitudeResponse> list() {
        return entries.findAllByUserIdOrderByEntryDateDescCreatedAtDesc(currentUser.id()).stream()
                .map(GratitudeMapper::toResponse)
                .toList();
    }

    public GratitudeResponse get(Long id) {
        return GratitudeMapper.toResponse(findOwned(id));
    }

    public Optional<GratitudeResponse> findRandom() {
        Long userId = currentUser.id();
        long count = entries.countByUserId(userId);
        if (count == 0) {
            return Optional.empty();
        }
        int index = ThreadLocalRandom.current().nextInt(Math.toIntExact(count));
        return entries.findByUserId(userId, PageRequest.of(index, 1, Sort.by("id"))).stream()
                .findFirst()
                .map(GratitudeMapper::toResponse);
    }

    @Transactional
    public GratitudeResponse update(Long id, GratitudeRequest request) {
        GratitudeEntry entry = findOwned(id);
        entry.change(resolveDate(request.entryDate()), request.title(), request.content(), request.moodTag());
        log.info("Updated gratitude entry {} for user {}", id, currentUser.id());
        return GratitudeMapper.toResponse(entry);
    }

    @Transactional
    public void delete(Long id) {
        GratitudeEntry entry = findOwned(id);
        entries.delete(entry);
        log.info("Deleted gratitude entry {} for user {}", id, currentUser.id());
    }

    private GratitudeEntry findOwned(Long id) {
        return entries.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new GratitudeNotFoundException(id));
    }

    private UserAccount currentAccount() {
        return users.findById(currentUser.id())
                .orElseThrow(() -> new IllegalStateException("Authenticated user was not found"));
    }

    private LocalDate resolveDate(LocalDate entryDate) {
        return entryDate == null ? LocalDate.now(clock) : entryDate;
    }
}
