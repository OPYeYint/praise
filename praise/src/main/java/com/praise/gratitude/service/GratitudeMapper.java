package com.praise.gratitude.service;

import com.praise.gratitude.domain.GratitudeEntry;
import com.praise.gratitude.web.GratitudeResponse;

public final class GratitudeMapper {

    private GratitudeMapper() {
    }

    public static GratitudeResponse toResponse(GratitudeEntry entry) {
        return new GratitudeResponse(
                entry.getId(),
                entry.getEntryDate(),
                entry.getTitle(),
                entry.getContent(),
                entry.getMoodTag(),
                entry.getCreatedAt());
    }
}
