package com.praise.gratitude.domain;

import com.praise.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "gratitude_entries")
public class GratitudeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "mood_tag", length = 50)
    private String moodTag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    protected GratitudeEntry() {
    }

    public static GratitudeEntry create(
            UserAccount user,
            LocalDate entryDate,
            String title,
            String content,
            String moodTag,
            Instant createdAt) {
        GratitudeEntry entry = new GratitudeEntry();
        entry.user = user;
        entry.entryDate = entryDate;
        entry.title = title;
        entry.content = content;
        entry.moodTag = moodTag;
        entry.createdAt = createdAt;
        return entry;
    }

    public void change(LocalDate entryDate, String title, String content, String moodTag) {
        this.entryDate = entryDate;
        this.title = title;
        this.content = content;
        this.moodTag = moodTag;
    }

    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getMoodTag() {
        return moodTag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UserAccount getUser() {
        return user;
    }
}
