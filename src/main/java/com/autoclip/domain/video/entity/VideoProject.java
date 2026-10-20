package com.autoclip.domain.video.entity;

import com.autoclip.common.entity.BaseEntity;
import com.autoclip.domain.auth.entity.User;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "video_projects")
public class VideoProject extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String scriptText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoStatus status = VideoStatus.DRAFT;

    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AspectRatio aspectRatio = AspectRatio.PORTRAIT_9_16;

    private String voice;

    private String audioUrl;

    private String outputUrl;
}
