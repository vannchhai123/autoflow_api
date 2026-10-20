package com.autoclip.domain.video.dto;

import com.autoclip.domain.video.entity.AspectRatio;
import com.autoclip.domain.video.entity.RenderJobStatus;
import com.autoclip.domain.video.entity.VideoProject;
import com.autoclip.domain.video.entity.VideoRenderJob;
import com.autoclip.domain.video.entity.VideoStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class VideoDtos {

    private VideoDtos() {
    }

    public record VideoProjectRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20_000) String scriptText,
            @Positive Integer durationSeconds,
            @NotNull AspectRatio aspectRatio,
            String voice,
            String audioUrl) {
    }

    public record VideoProjectResponse(
            UUID id, String title, String scriptText, VideoStatus status,
            Integer durationSeconds, AspectRatio aspectRatio, String voice, String audioUrl, String outputUrl,
            Instant createdAt, Instant updatedAt) {

        public static VideoProjectResponse from(VideoProject p) {
            return new VideoProjectResponse(p.getId(), p.getTitle(), p.getScriptText(), p.getStatus(),
                    p.getDurationSeconds(), p.getAspectRatio(), p.getVoice(), p.getAudioUrl(), p.getOutputUrl(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    public record RenderJobResponse(
            UUID jobId, RenderJobStatus status, int progressPercentage, String errorMessage) {

        public static RenderJobResponse from(VideoRenderJob j) {
            return new RenderJobResponse(j.getJobId(), j.getStatus(), j.getProgressPercentage(), j.getErrorMessage());
        }
    }

    public record AudioUploadResponse(
            String audioUrl, String filename) {
    }

    public record ClonedVoiceResponse(
            UUID id, String name, String baseVoice, String audioSampleUrl, Instant createdAt) {

        public static ClonedVoiceResponse from(com.autoclip.domain.video.entity.ClonedVoice v) {
            return new ClonedVoiceResponse(v.getId(), v.getName(), v.getBaseVoice(), v.getAudioSampleUrl(), v.getCreatedAt());
        }
    }
}
