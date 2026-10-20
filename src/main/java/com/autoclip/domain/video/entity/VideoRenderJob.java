package com.autoclip.domain.video.entity;

import com.autoclip.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "video_render_jobs")
public class VideoRenderJob extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID jobId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_project_id", nullable = false)
    private VideoProject videoProject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RenderJobStatus status = RenderJobStatus.QUEUED;

    @Column(nullable = false)
    private int progressPercentage;

    @Column(length = 2000)
    private String errorMessage;
}
