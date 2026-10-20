package com.autoclip.domain.publishing.repository;

import com.autoclip.domain.publishing.entity.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

    List<SocialAccount> findByOwnerId(UUID ownerId);

    Optional<SocialAccount> findFirstByOwnerIdAndPlatform(UUID ownerId, com.autoclip.domain.publishing.entity.Platform platform);
}
