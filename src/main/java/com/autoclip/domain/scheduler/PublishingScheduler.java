package com.autoclip.domain.scheduler;

import com.autoclip.domain.publishing.service.PublishingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class PublishingScheduler {

    private final PublishingService publishingService;

    @Value("${autoclip.scheduler.batch-size:20}")
    private int batchSize;

    @Scheduled(cron = "${autoclip.scheduler.publish-cron:0 * * * * *}")
    public void dispatchDueTasks() {
        List<UUID> claimed = publishingService.claimDueTasks(Instant.now(), batchSize);
        if (claimed.isEmpty()) {
            return;
        }
        log.info("Dispatching {} publishing task(s)", claimed.size());
        claimed.forEach(publishingService::publishAsync);
    }
}
