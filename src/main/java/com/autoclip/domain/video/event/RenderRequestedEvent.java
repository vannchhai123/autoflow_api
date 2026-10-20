package com.autoclip.domain.video.event;

import java.util.UUID;

public record RenderRequestedEvent(UUID projectId, UUID renderJobId) {
}
