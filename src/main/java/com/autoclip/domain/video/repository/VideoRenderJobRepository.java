package com.autoclip.domain.video.repository;

import com.autoclip.domain.video.entity.VideoRenderJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VideoRenderJobRepository extends JpaRepository<VideoRenderJob, UUID> {

    Optional<VideoRenderJob> findByJobId(UUID jobId);

    @Modifying
    @Query("delete from VideoRenderJob j where j.videoProject.id = :videoProjectId")
    void deleteByVideoProjectId(@Param("videoProjectId") UUID videoProjectId);
}
