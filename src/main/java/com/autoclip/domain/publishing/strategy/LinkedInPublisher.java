package com.autoclip.domain.publishing.strategy;

import com.autoclip.domain.publishing.entity.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class LinkedInPublisher implements SocialPublisher {

    @Override
    public Platform platform() {
        return Platform.LINKEDIN;
    }

    @Override
    public PublishResult publish(PublishRequest request) {
        log.info("Publishing '{}' to LinkedIn {}", request.title(), request.channelName());
        
        String id = "urn:li:ugcPost:" + UUID.randomUUID();
        return new PublishResult(id, "https://linkedin.com/feed/update/" + id);
    }
}
