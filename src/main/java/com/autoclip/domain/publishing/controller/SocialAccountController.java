package com.autoclip.domain.publishing.controller;

import com.autoclip.domain.auth.entity.User;
import com.autoclip.domain.publishing.dto.SocialAccountResponse;
import com.autoclip.domain.publishing.entity.AccountStatus;
import com.autoclip.domain.publishing.entity.Platform;
import com.autoclip.domain.publishing.entity.SocialAccount;
import com.autoclip.domain.publishing.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class SocialAccountController {

    private final SocialAccountRepository accountRepository;
    private final com.autoclip.domain.auth.repository.UserRepository userRepository;
    private final com.autoclip.domain.publishing.service.YouTubeOAuthService youTubeOAuthService;
    private final com.autoclip.domain.publishing.service.TikTokOAuthService tikTokOAuthService;
    private final com.autoclip.domain.publishing.service.FacebookOAuthService facebookOAuthService;

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

    @GetMapping
    public ResponseEntity<List<SocialAccountResponse>> listAccounts(@AuthenticationPrincipal User user) {
        User target = resolveUser(user);
        if (target == null) {
            return ResponseEntity.ok(List.of());
        }
        List<SocialAccountResponse> accounts = accountRepository.findByOwnerId(target.getId())
                .stream()
                .map(SocialAccountResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/connect/{platform}")
    public ResponseEntity<Map<String, String>> initiateConnect(
            @PathVariable Platform platform,
            @AuthenticationPrincipal User user) {
        User target = resolveUser(user);
        UUID userId = target != null ? target.getId() : null;

        if (platform == Platform.YOUTUBE_SHORTS) {
            String authUrl = youTubeOAuthService.buildAuthorizationUrl(userId);
            return ResponseEntity.ok(Map.of("authorizationUrl", authUrl));
        }
        if (platform == Platform.TIKTOK) {
            String authUrl = tikTokOAuthService.buildAuthorizationUrl(userId);
            return ResponseEntity.ok(Map.of("authorizationUrl", authUrl));
        }
        if (platform == Platform.FACEBOOK) {
            String authUrl = facebookOAuthService.buildAuthorizationUrl(userId);
            return ResponseEntity.ok(Map.of("authorizationUrl", authUrl));
        }

        String authUrl = "https://oauth.clipflow.app/connect/" + platform.name().toLowerCase() + "?state=" + (target != null ? target.getId() : "");
        return ResponseEntity.ok(Map.of("authorizationUrl", authUrl));
    }

    @GetMapping("/oauth2/callback/youtube")
    public org.springframework.web.servlet.view.RedirectView handleYouTubeCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        String redirectUrl = youTubeOAuthService.handleCallback(code, state);
        return new org.springframework.web.servlet.view.RedirectView(redirectUrl);
    }

    @GetMapping("/oauth2/callback/tiktok")
    public org.springframework.web.servlet.view.RedirectView handleTikTokCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        String redirectUrl = tikTokOAuthService.handleCallback(code, state);
        return new org.springframework.web.servlet.view.RedirectView(redirectUrl);
    }

    @GetMapping("/oauth2/callback/facebook")
    public org.springframework.web.servlet.view.RedirectView handleFacebookCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        String redirectUrl = facebookOAuthService.handleCallback(code, state);
        return new org.springframework.web.servlet.view.RedirectView(redirectUrl);
    }

    @PostMapping("/{id}/reconnect")
    public ResponseEntity<SocialAccountResponse> reconnect(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        User target = resolveUser(user);
        SocialAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Social account not found"));

        if (target == null || !account.getOwner().getId().equals(target.getId())) {
            throw new IllegalArgumentException("Social account not found or access denied");
        }

        account.setStatus(AccountStatus.CONNECTED);
        account.setTokenExpiresAt(Instant.now().plusSeconds(60 * 86400));
        SocialAccount saved = accountRepository.save(account);

        return ResponseEntity.ok(SocialAccountResponse.fromEntity(saved));
    }

    @PostMapping("/config/credentials")
    public ResponseEntity<Map<String, Object>> saveCredentials(@RequestBody Map<String, String> body) {
        String platform = body.get("platform");
        String clientId = body.get("clientId") != null ? body.get("clientId").trim() : "";
        String clientSecret = body.get("clientSecret") != null ? body.get("clientSecret").trim() : "";

        if ("YOUTUBE_SHORTS".equalsIgnoreCase(platform) || "GOOGLE".equalsIgnoreCase(platform) || "YOUTUBE".equalsIgnoreCase(platform)) {
            if (!clientId.isEmpty() && (clientId.contains("@") || !clientId.endsWith(".apps.googleusercontent.com"))) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid Google Client ID. An email address cannot be used. Google OAuth Client IDs end in .apps.googleusercontent.com"
                ));
            }
            youTubeOAuthService.setCredentials(clientId, clientSecret);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "platform", "YOUTUBE_SHORTS",
                    "configured", youTubeOAuthService.hasCredentials()
            ));
        }

        if ("TIKTOK".equalsIgnoreCase(platform)) {
            tikTokOAuthService.setCredentials(clientId, clientSecret);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "platform", "TIKTOK",
                    "configured", tikTokOAuthService.hasCredentials()
            ));
        }

        if ("FACEBOOK".equalsIgnoreCase(platform)) {
            facebookOAuthService.setCredentials(clientId, clientSecret);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "platform", "FACEBOOK",
                    "configured", facebookOAuthService.hasCredentials()
            ));
        }

        return ResponseEntity.badRequest().body(Map.of("error", "Unsupported platform: " + platform));
    }

    @PostMapping("/connect-demo/{platform}")
    public ResponseEntity<SocialAccountResponse> connectDemo(
            @PathVariable Platform platform,
            @AuthenticationPrincipal User user) {
        User target = resolveUser(user);
        UUID userId = target != null ? target.getId() : null;

        if (platform == Platform.YOUTUBE_SHORTS) {
            SocialAccount account = youTubeOAuthService.connectDemoChannel(userId);
            return ResponseEntity.ok(SocialAccountResponse.fromEntity(account));
        }

        if (platform == Platform.TIKTOK) {
            SocialAccount account = tikTokOAuthService.connectDemoChannel(userId);
            return ResponseEntity.ok(SocialAccountResponse.fromEntity(account));
        }

        SocialAccount account = accountRepository.findFirstByOwnerIdAndPlatform(target.getId(), platform)
                .orElseGet(() -> {
                    SocialAccount a = new SocialAccount();
                    a.setOwner(target);
                    a.setPlatform(platform);
                    return a;
                });
        account.setChannelName("AutoClip " + platform.name() + " Channel");
        account.setAvatarUrl("https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80");
        account.setAccessToken("demo_" + platform.name().toLowerCase() + "_token_" + UUID.randomUUID());
        account.setTokenExpiresAt(Instant.now().plusSeconds(60 * 86400));
        account.setStatus(AccountStatus.CONNECTED);
        account.setFollowersCount(12500L);
        account.setAccountType("CREATOR");

        SocialAccount saved = accountRepository.save(account);
        return ResponseEntity.ok(SocialAccountResponse.fromEntity(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disconnect(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        User target = resolveUser(user);
        if (target != null) {
            accountRepository.findById(id).ifPresent(account -> {
                if (account.getOwner().getId().equals(target.getId())) {
                    accountRepository.delete(account);
                }
            });
        }
        return ResponseEntity.noContent().build();
    }
}
