package com.autoclip.domain.video.service;

import com.autoclip.common.config.AsyncConfig;
import com.autoclip.domain.video.entity.RenderJobStatus;
import com.autoclip.domain.video.entity.VideoProject;
import com.autoclip.domain.video.entity.VideoRenderJob;
import com.autoclip.domain.video.entity.VideoStatus;
import com.autoclip.domain.video.event.RenderRequestedEvent;
import com.autoclip.domain.video.repository.VideoProjectRepository;
import com.autoclip.domain.video.repository.VideoRenderJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoRenderWorker {

    private final VideoRenderJobRepository jobRepository;
    private final VideoProjectRepository projectRepository;
    private final VideoEngineService videoEngineService;

    @Async(AsyncConfig.TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRenderRequested(RenderRequestedEvent event) {
        VideoRenderJob job = jobRepository.findById(event.renderJobId()).orElseThrow();
        VideoProject project = projectRepository.findById(event.projectId()).orElseThrow();

        try {
            job.setStatus(RenderJobStatus.RUNNING);
            jobRepository.save(job);

            String outputUrl = videoEngineService.render(project, job);

            job.setProgressPercentage(100);
            job.setStatus(RenderJobStatus.SUCCEEDED);
            jobRepository.save(job);

            finishProject(event, VideoStatus.COMPLETED, outputUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(job, event, "Render interrupted");
        } catch (Exception e) {
            log.error("Render job {} failed", event.renderJobId(), e);
            fail(job, event, e.getMessage());
        }
    }

    private void fail(VideoRenderJob job, RenderRequestedEvent event, String message) {
        job.setStatus(RenderJobStatus.FAILED);
        job.setErrorMessage(message);
        jobRepository.save(job);
        finishProject(event, VideoStatus.FAILED, null);
    }

    private void finishProject(RenderRequestedEvent event, VideoStatus status, String outputUrl) {
        VideoProject project = projectRepository.findById(event.projectId()).orElseThrow();
        project.setStatus(status);
        if (outputUrl != null) {
            project.setOutputUrl(outputUrl);
        }
        projectRepository.save(project);
    }
}
