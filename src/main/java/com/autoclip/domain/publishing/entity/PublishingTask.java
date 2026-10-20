package com.autoclip.domain.publishing.entity;

import com.autoclip.common.entity.BaseEntity;
import com.autoclip.domain.video.entity.VideoProject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "publishing_tasks",
        indexes = @Index(name = "idx_publishing_tasks_status_scheduled", columnList = "status, scheduledTime"))
public class PublishingTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_project_id", nullable = false)
    private VideoProject videoProject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "social_account_id", nullable = false)
    private SocialAccount socialAccount;

    private String caption;

    @Column(nullable = false)
    private Instant scheduledTime;

    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublishingStatus status = PublishingStatus.PENDING;

    private String publicPostUrl;

    private String customThumbnailUrl;

    private Boolean isShort;

    @Column(length = 2000)
    private String errorMessage;
}
