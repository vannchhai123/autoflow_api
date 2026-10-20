package com.autoclip.domain.video.repository;

import com.autoclip.domain.video.entity.ClonedVoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClonedVoiceRepository extends JpaRepository<ClonedVoice, UUID> {

    List<ClonedVoice> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<ClonedVoice> findByIdAndOwnerId(UUID id, UUID ownerId);
}
