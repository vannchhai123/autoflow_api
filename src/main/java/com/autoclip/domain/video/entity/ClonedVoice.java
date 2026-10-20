package com.autoclip.domain.video.entity;

import com.autoclip.common.entity.BaseEntity;
import com.autoclip.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "cloned_voices")
public class ClonedVoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String baseVoice;

    @Column(nullable = false)
    private String audioSampleUrl;

    @Column(columnDefinition = "text", nullable = false)
    private String profileJson;
}
