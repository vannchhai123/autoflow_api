package com.autoclip.domain.publishing.strategy;

import com.autoclip.common.exception.BusinessException;
import com.autoclip.domain.publishing.entity.Platform;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PublisherFactory {

    private final Map<Platform, SocialPublisher> publishers = new EnumMap<>(Platform.class);

    public PublisherFactory(List<SocialPublisher> strategies) {
        for (SocialPublisher strategy : strategies) {
            SocialPublisher previous = publishers.put(strategy.platform(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Duplicate publisher registered for " + strategy.platform());
            }
        }
    }

    public SocialPublisher getPublisher(Platform platform) {
        SocialPublisher publisher = publishers.get(platform);
        if (publisher == null) {
            throw new BusinessException("No publisher configured for platform " + platform);
        }
        return publisher;
    }
}
