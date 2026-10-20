package com.autoclip.domain.publishing.strategy;

import com.autoclip.domain.publishing.entity.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InstagramPublisher implements SocialPublisher {

    @Override
    public Platform platform() {
        return Platform.INSTAGRAM_REELS;
    }

    @Override
    public PublishResult publish(PublishRequest request) {
        log.info("Publishing '{}' to Instagram Reels account {}", request.title(), request.channelName());
        
        String id = "ig-" + java.util.UUID.randomUUID();
        return new PublishResult(id, "https://instagram.com/reel/" + id);
    }
}
