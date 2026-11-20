package com.praise.gratitude.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GratitudeRequest(
        LocalDate entryDate,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String content,
        @Size(max = 50) String moodTag) {

    public GratitudeRequest {
        title = title == null ? null : title.trim();
        content = content == null ? null : content.trim();
        moodTag = moodTag == null || moodTag.isBlank() ? null : moodTag.trim();
    }
}
