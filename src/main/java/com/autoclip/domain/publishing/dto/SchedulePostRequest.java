package com.autoclip.domain.publishing.dto;

import com.autoclip.domain.publishing.entity.Platform;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SchedulePostRequest(
        UUID videoProjectId,
        List<Platform> platforms,
        String title,
        String caption,
        List<String> hashtags,
        Instant scheduledTime,
        String thumbnailUrl,
        Boolean isShort
) {
    public SchedulePostRequest(UUID videoProjectId, List<Platform> platforms, String title,
                               String caption, List<String> hashtags, Instant scheduledTime) {
        this(videoProjectId, platforms, title, caption, hashtags, scheduledTime, null, null);
    }
}
