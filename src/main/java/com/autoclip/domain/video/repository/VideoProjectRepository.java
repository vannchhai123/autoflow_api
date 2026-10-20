package com.autoclip.domain.video.repository;

import com.autoclip.domain.video.entity.VideoProject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VideoProjectRepository extends JpaRepository<VideoProject, UUID> {

    Page<VideoProject> findByOwnerId(UUID ownerId, Pageable pageable);

    Optional<VideoProject> findByIdAndOwnerId(UUID id, UUID ownerId);
}
