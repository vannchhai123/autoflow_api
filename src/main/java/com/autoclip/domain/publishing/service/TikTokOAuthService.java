package com.autoclip.domain.publishing.service;

import com.autoclip.domain.auth.entity.User;
import com.autoclip.domain.auth.repository.UserRepository;
import com.autoclip.domain.publishing.entity.AccountStatus;
import com.autoclip.domain.publishing.entity.Platform;
import com.autoclip.domain.publishing.entity.SocialAccount;
import com.autoclip.domain.publishing.repository.SocialAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TikTokOAuthService {

    @Value("${clipflow.oauth.tiktok.client-key:}")
    private String clientKey;

    @Value("${clipflow.oauth.tiktok.client-secret:}")
    private String clientSecret;

    @Value("${clipflow.oauth.tiktok.redirect-uri:http://localhost:8090/api/accounts/oauth2/callback/tiktok}")
    private String redirectUri;

    @Value("${clipflow.oauth.tiktok.frontend-redirect:http://localhost:3000/settings/accounts}")
    private String frontendRedirect;

    @Value("${autoclip.admin.email:}")
    private String adminEmail;

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @PostConstruct
    public void init() {
        if (redirectUri == null || redirectUri.isBlank()) {
            redirectUri = "http://localhost:8090/api/accounts/oauth2/callback/tiktok";
        }
        if (frontendRedirect == null || frontendRedirect.isBlank()) {
            frontendRedirect = "http://localhost:3000/settings/accounts";
        }
        loadSavedCredentials();
    }

    public synchronized void setCredentials(String newKey, String newSecret) {
        this.clientKey = (newKey != null) ? newKey.trim() : "";
        this.clientSecret = (newSecret != null) ? newSecret.trim() : "";
        saveCredentialsToFile();
    }

    public boolean hasCredentials() {
        return clientKey != null && !clientKey.isBlank();
    }

    private User resolveDefaultUser() {
        if (adminEmail != null && !adminEmail.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmail(adminEmail.trim());
            if (byEmail.isPresent()) return byEmail.get();
        }
        return userRepository.findAll().stream().findFirst().orElse(null);
    }

    private void loadSavedCredentials() {
        try {
            Path file = Paths.get("storage", "oauth_credentials.json");
            if (Files.exists(file)) {
                JsonNode root = objectMapper.readTree(file.toFile());
                JsonNode tiktok = root.path("tiktok");
                if (tiktok.has("clientKey") && (this.clientKey == null || this.clientKey.isBlank())) {
                    this.clientKey = tiktok.get("clientKey").asText();
                }
                if (tiktok.has("clientSecret") && (this.clientSecret == null || this.clientSecret.isBlank())) {
                    this.clientSecret = tiktok.get("clientSecret").asText();
                }
                log.info("Loaded persisted TikTok OAuth credentials from storage/oauth_credentials.json (configured: {})", hasCredentials());
            }
        } catch (Exception e) {
            log.warn("Could not load TikTok credentials from storage: {}", e.getMessage());
        }
    }

    private void saveCredentialsToFile() {
        try {
            Path storageDir = Paths.get("storage");
            Files.createDirectories(storageDir);
            Path file = storageDir.resolve("oauth_credentials.json");
            Map<String, Object> map = new java.util.HashMap<>();
            if (Files.exists(file)) {
                try {
                    JsonNode existing = objectMapper.readTree(file.toFile());
                    if (existing.isObject()) {
                        map = objectMapper.convertValue(existing, Map.class);
                    }
                } catch (Exception ignored) {}
            }
            Map<String, String> tiktokMap = new java.util.HashMap<>();
            tiktokMap.put("clientKey", this.clientKey != null ? this.clientKey : "");
            tiktokMap.put("clientSecret", this.clientSecret != null ? this.clientSecret : "");
            map.put("tiktok", tiktokMap);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), map);
            log.info("Persisted TikTok OAuth credentials to storage/oauth_credentials.json");
        } catch (Exception e) {
            log.error("Failed to persist TikTok OAuth credentials: {}", e.getMessage());
        }
    }

    @Transactional
    public SocialAccount connectDemoChannel(UUID userId) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        if (user == null) {
            user = resolveDefaultUser();
        }
        if (user == null) {
            throw new IllegalStateException("No user found for demo channel");
        }

        User targetUser = user;
        SocialAccount account = socialAccountRepository.findFirstByOwnerIdAndPlatform(targetUser.getId(), Platform.TIKTOK)
                .orElseGet(() -> {
                    SocialAccount a = new SocialAccount();
                    a.setOwner(targetUser);
                    a.setPlatform(Platform.TIKTOK);
                    return a;
                });

        account.setChannelName("@vannchay_creator");
        account.setAvatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80");
        account.setAccessToken("demo_tiktok_token_" + UUID.randomUUID());
        account.setRefreshToken("demo_tiktok_refresh_" + UUID.randomUUID());
        account.setTokenExpiresAt(Instant.now().plusSeconds(60 * 86400));
        account.setStatus(AccountStatus.CONNECTED);
        account.setFollowersCount(24500L);
        account.setAccountType("CREATOR");

        return socialAccountRepository.save(account);
    }

    public String buildAuthorizationUrl(UUID userId) {
        if (!hasCredentials()) {
            log.warn("TIKTOK_CLIENT_KEY is not configured or is a mock key.");
            return frontendRedirect + "?error=missing_credentials_tiktok";
        }

        String scope = "user.info.basic,video.publish,video.upload";

        return "https://www.tiktok.com/v2/auth/authorize/?" +
                "client_key=" + URLEncoder.encode(clientKey, StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&state=" + (userId != null ? userId.toString() : "demo");
    }

    @Transactional
    public String handleCallback(String code, String state) {
        try {
            log.info("Handling TikTok OAuth callback with code...");

            String requestBody = "client_key=" + URLEncoder.encode(clientKey, StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                    "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                    "&grant_type=authorization_code" +
                    "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

            HttpRequest tokenReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/oauth/token/"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> tokenRes = httpClient.send(tokenReq, HttpResponse.BodyHandlers.ofString());

            if (tokenRes.statusCode() != 200) {
                log.error("Failed to exchange TikTok code for token. HTTP {} -> {}", tokenRes.statusCode(), tokenRes.body());
                return frontendRedirect + "?error=tiktok_token_exchange_failed";
            }

            JsonNode tokenJson = objectMapper.readTree(tokenRes.body());
            JsonNode dataNode = tokenJson.path("data");
            String accessToken = dataNode.path("access_token").asText(tokenJson.path("access_token").asText(""));
            String refreshToken = dataNode.path("refresh_token").asText(tokenJson.path("refresh_token").asText(null));
            long expiresIn = dataNode.path("expires_in").asLong(tokenJson.path("expires_in").asLong(86400));

            HttpRequest userReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/user/info/?fields=open_id,union_id,avatar_url,display_name,follower_count"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> userRes = httpClient.send(userReq, HttpResponse.BodyHandlers.ofString());

            String channelName = "@tiktok_creator";
            String avatarUrl = "";
            long followersCount = 0;

            if (userRes.statusCode() == 200) {
                JsonNode userJson = objectMapper.readTree(userRes.body());
                JsonNode userData = userJson.path("data").path("user");
                channelName = "@" + userData.path("display_name").asText("tiktok_creator");
                avatarUrl = userData.path("avatar_url").asText("");
                followersCount = userData.path("follower_count").asLong(0);
            } else {
                log.warn("Could not query TikTok user profile: {}", userRes.body());
            }

            User user = null;
            if (state != null && !state.isBlank() && !"demo".equalsIgnoreCase(state)) {
                try {
                    UUID userId = UUID.fromString(state);
                    user = userRepository.findById(userId).orElse(null);
                } catch (IllegalArgumentException ignored) {}
            }
            if (user == null) {
                user = resolveDefaultUser();
            }
            if (user == null) {
                log.error("No valid user found to bind TikTok channel");
                return frontendRedirect + "?error=user_not_found";
            }

            User targetUser = user;
            SocialAccount account = socialAccountRepository.findFirstByOwnerIdAndPlatform(targetUser.getId(), Platform.TIKTOK)
                    .orElseGet(() -> {
                        SocialAccount a = new SocialAccount();
                        a.setOwner(targetUser);
                        a.setPlatform(Platform.TIKTOK);
                        return a;
                    });

            account.setChannelName(channelName);
            account.setAvatarUrl(avatarUrl);
            account.setAccessToken(accessToken);
            if (refreshToken != null && !refreshToken.isBlank()) {
                account.setRefreshToken(refreshToken);
            }
            account.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
            account.setStatus(AccountStatus.CONNECTED);
            account.setFollowersCount(followersCount);
            account.setAccountType("BUSINESS");

            socialAccountRepository.save(account);
            log.info("Successfully linked TikTok account '{}' to user {}", channelName, user.getEmail());

            return frontendRedirect + "?connected=TIKTOK";
        } catch (Exception e) {
            log.error("Error processing TikTok OAuth callback", e);
            return frontendRedirect + "?error=tiktok_callback_exception";
        }
    }
}
