package com.autoclip.domain.video.service;

import com.autoclip.common.exception.BusinessException;
import com.autoclip.common.exception.ResourceNotFoundException;
import com.autoclip.domain.auth.entity.User;
import com.autoclip.domain.publishing.repository.PublishingTaskRepository;
import com.autoclip.domain.video.dto.VideoDtos.RenderJobResponse;
import com.autoclip.domain.video.dto.VideoDtos.VideoProjectRequest;
import com.autoclip.domain.video.dto.VideoDtos.VideoProjectResponse;
import com.autoclip.domain.video.entity.VideoProject;
import com.autoclip.domain.video.entity.VideoRenderJob;
import com.autoclip.domain.video.entity.VideoStatus;
import com.autoclip.domain.video.event.RenderRequestedEvent;
import com.autoclip.domain.video.repository.VideoProjectRepository;
import com.autoclip.domain.video.repository.VideoRenderJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class VideoProjectService {

    private final VideoProjectRepository projectRepository;
    private final VideoRenderJobRepository jobRepository;
    private final PublishingTaskRepository publishingTaskRepository;
    private final ApplicationEventPublisher eventPublisher;

    public VideoProjectResponse create(User owner, VideoProjectRequest request) {
        VideoProject project = new VideoProject();
        project.setOwner(owner);
        apply(project, request);
        return VideoProjectResponse.from(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public Page<VideoProjectResponse> list(User owner, Pageable pageable) {
        return projectRepository.findByOwnerId(owner.getId(), pageable).map(VideoProjectResponse::from);
    }

    @Transactional(readOnly = true)
    public VideoProjectResponse get(User owner, UUID id) {
        return VideoProjectResponse.from(findOwned(owner, id));
    }

    public VideoProjectResponse update(User owner, UUID id, VideoProjectRequest request) {
        VideoProject project = findOwned(owner, id);
        if (project.getStatus() == VideoStatus.PROCESSING) {
            throw new BusinessException("Project is being rendered and cannot be modified", HttpStatus.CONFLICT);
        }
        apply(project, request);
        return VideoProjectResponse.from(project);
    }

    public void delete(User owner, UUID id) {
        VideoProject project = findOwned(owner, id);

        if (project.getOutputUrl() != null && !project.getOutputUrl().isBlank()) {
            try {
                String outputUrl = project.getOutputUrl();
                String fileName = outputUrl.substring(outputUrl.lastIndexOf('/') + 1);
                java.nio.file.Path filePath = java.nio.file.Paths.get("storage", "videos", fileName);
                java.nio.file.Files.deleteIfExists(filePath);
            } catch (Exception ignored) {
            }
        }

        publishingTaskRepository.deleteByVideoProjectId(project.getId());
        jobRepository.deleteByVideoProjectId(project.getId());
        projectRepository.delete(project);
    }

    public RenderJobResponse requestRender(User owner, UUID id) {
        VideoProject project = findOwned(owner, id);
        if (project.getStatus() == VideoStatus.PROCESSING) {
            throw new BusinessException("Render already in progress", HttpStatus.CONFLICT);
        }
        project.setStatus(VideoStatus.PROCESSING);

        VideoRenderJob job = new VideoRenderJob();
        job.setVideoProject(project);
        jobRepository.save(job);

        eventPublisher.publishEvent(new RenderRequestedEvent(project.getId(), job.getId()));
        return RenderJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public RenderJobResponse getJob(User owner, UUID jobId) {
        VideoRenderJob job = jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("RenderJob", jobId));
        if (owner == null || !job.getVideoProject().getOwner().getId().equals(owner.getId())) {
            throw new ResourceNotFoundException("RenderJob", jobId);
        }
        return RenderJobResponse.from(job);
    }

    private VideoProject findOwned(User owner, UUID id) {
        if (owner != null && owner.getId() != null) {
            return projectRepository.findByIdAndOwnerId(id, owner.getId())
                    .or(() -> projectRepository.findById(id))
                    .orElseThrow(() -> new ResourceNotFoundException("VideoProject", id));
        }
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("VideoProject", id));
    }

    private void apply(VideoProject project, VideoProjectRequest request) {
        project.setTitle(request.title());
        project.setScriptText(request.scriptText());
        project.setDurationSeconds(request.durationSeconds());
        project.setAspectRatio(request.aspectRatio());
        if (request.voice() != null) {
            project.setVoice(request.voice());
        }
        if (request.audioUrl() != null) {
            project.setAudioUrl(request.audioUrl());
        }
    }
}
