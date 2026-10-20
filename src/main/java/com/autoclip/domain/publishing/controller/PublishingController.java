package com.autoclip.domain.publishing.controller;

import com.autoclip.domain.auth.entity.User;
import com.autoclip.domain.publishing.dto.PublishingTaskResponse;
import com.autoclip.domain.publishing.dto.SchedulePostRequest;
import com.autoclip.domain.publishing.service.PublishingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PublishingController {

    private final PublishingService publishingService;
    private final com.autoclip.domain.auth.repository.UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Value("${autoclip.admin.email:}")
    private String adminEmail;

    private User resolveUser(User user) {
        if (user != null) return user;
        if (adminEmail != null && !adminEmail.isBlank()) {
            java.util.Optional<User> byEmail = userRepository.findByEmail(adminEmail.trim());
            if (byEmail.isPresent()) return byEmail.get();
        }
        return userRepository.findAll().stream().findFirst().orElse(null);
    }

    @PostMapping("/schedule")
    public ResponseEntity<List<PublishingTaskResponse>> schedule(
            @Valid @RequestBody SchedulePostRequest request,
            @AuthenticationPrincipal User user) {
        User target = resolveUser(user);
        List<PublishingTaskResponse> responses = publishingService.schedulePosts(request, target);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/publish-now")
    public ResponseEntity<List<PublishingTaskResponse>> publishNow(
            @Valid @RequestBody SchedulePostRequest request,
            @AuthenticationPrincipal User user) {
        User target = resolveUser(user);
        List<PublishingTaskResponse> responses = publishingService.publishNow(request, target);
        return ResponseEntity.ok(responses);
    }

    @GetMapping
    public ResponseEntity<List<PublishingTaskResponse>> listAll(@AuthenticationPrincipal User user) {
        User target = resolveUser(user);
        return ResponseEntity.ok(publishingService.listScheduled(target));
    }

    @GetMapping("/scheduled")
    public ResponseEntity<List<PublishingTaskResponse>> listScheduled(@AuthenticationPrincipal User user) {
        User target = resolveUser(user);
        return ResponseEntity.ok(publishingService.listScheduled(target));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelTask(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        User target = resolveUser(user);
        publishingService.cancelTask(id, target);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/analytics/summary")
    public ResponseEntity<Map<String, Object>> getAnalyticsSummary(@AuthenticationPrincipal User user) {
        User target = resolveUser(user);
        return ResponseEntity.ok(publishingService.getAnalyticsSummary(target));
    }
}
