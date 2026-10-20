package com.autoclip.domain.publishing.dto;

import com.autoclip.domain.publishing.entity.AccountStatus;
import com.autoclip.domain.publishing.entity.Platform;
import com.autoclip.domain.publishing.entity.SocialAccount;

import java.time.Instant;
import java.util.UUID;

public record SocialAccountResponse(
        UUID id,
        Platform platform,
        String accountName,
        String avatarUrl,
        AccountStatus status,
        Instant tokenExpiresAt,
        String accountType,
        Instant connectedAt,
        Long followersCount
) {
    public static SocialAccountResponse fromEntity(SocialAccount account) {
        return new SocialAccountResponse(
                account.getId(),
                account.getPlatform(),
                account.getChannelName(),
                account.getAvatarUrl(),
                account.getStatus(),
                account.getTokenExpiresAt(),
                account.getAccountType(),
                account.getCreatedAt(),
                account.getFollowersCount()
        );
    }
}
