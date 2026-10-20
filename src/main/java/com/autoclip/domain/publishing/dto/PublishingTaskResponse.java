package com.autoclip.domain.publishing.dto;

import com.autoclip.domain.publishing.entity.Platform;
import com.autoclip.domain.publishing.entity.PublishingStatus;
import com.autoclip.domain.publishing.entity.PublishingTask;

import java.time.Instant;
import java.util.UUID;

public record PublishingTaskResponse(
        UUID id,
        UUID videoProjectId,
        String videoTitle,
        Platform platform,
        String channelName,
        PublishingStatus status,
        String caption,
        Instant scheduledTime,
        Instant publishedAt,
        String publicPostUrl,
        String errorMessage
) {
    public static PublishingTaskResponse fromEntity(PublishingTask task) {
        return new PublishingTaskResponse(
                task.getId(),
                task.getVideoProject() != null ? task.getVideoProject().getId() : null,
                task.getVideoProject() != null ? task.getVideoProject().getTitle() : null,
                task.getSocialAccount() != null ? task.getSocialAccount().getPlatform() : null,
                task.getSocialAccount() != null ? task.getSocialAccount().getChannelName() : null,
                task.getStatus(),
                task.getCaption(),
                task.getScheduledTime(),
                task.getPublishedAt(),
                task.getPublicPostUrl(),
                task.getErrorMessage()
        );
    }
}
