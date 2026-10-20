package com.autoclip.domain.video.repository;

import com.autoclip.domain.video.entity.VideoRenderJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VideoRenderJobRepository extends JpaRepository<VideoRenderJob, UUID> {

    Optional<VideoRenderJob> findByJobId(UUID jobId);
}
