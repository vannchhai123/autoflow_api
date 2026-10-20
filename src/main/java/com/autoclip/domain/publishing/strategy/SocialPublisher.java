package com.autoclip.domain.publishing.strategy;

import com.autoclip.domain.publishing.entity.Platform;

public interface SocialPublisher {

    Platform platform();

    PublishResult publish(PublishRequest request);

    record PublishRequest(String videoUrl, String title, String caption,
                          String accessToken, String channelName,
                          String thumbnailUrl, Boolean isShort) {

        public PublishRequest(String videoUrl, String title, String caption,
                              String accessToken, String channelName) {
            this(videoUrl, title, caption, accessToken, channelName, null, null);
        }
    }

    record PublishResult(String externalPostId, String publicPostUrl) {
    }
}
