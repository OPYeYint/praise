package com.praise.gratitude.web;

import java.time.Instant;
import java.time.LocalDate;

public record GratitudeResponse(
        Long id,
        LocalDate entryDate,
        String title,
        String content,
        String moodTag,
        Instant createdAt) {
}
