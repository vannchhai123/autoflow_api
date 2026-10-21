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

    @Value("${clipflow.oauth.tiktok.scope:user.info.basic,video.publish,video.upload}")
    private String scope;

    @Value("${clipflow.oauth.tiktok.frontend-redirect:https://autoflow-app-phi.vercel.app/settings/accounts}")
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
        String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        String renderUrl = System.getenv("RENDER_EXTERNAL_URL");
        if (redirectUri == null || redirectUri.isBlank()
                || (redirectUri.contains("localhost") && ("prod".equalsIgnoreCase(activeProfile) || (renderUrl != null && !renderUrl.isBlank())))) {
            String base = (renderUrl != null && !renderUrl.isBlank()) ? renderUrl.replaceAll("/+$", "") : "https://autoflow-api-f5c6.onrender.com";
            redirectUri = base + "/api/accounts/oauth2/callback/tiktok";
        }
        if (frontendRedirect == null || frontendRedirect.isBlank()) {
            frontendRedirect = "https://autoflow-app-phi.vercel.app/settings/accounts";
        }
        loadSavedCredentials();
    }

    public synchronized void setCredentials(String newKey, String newSecret) {
        this.clientKey = (newKey != null) ? newKey.trim() : "";
        this.clientSecret = (newSecret != null) ? newSecret.trim() : "";
        saveCredentialsToFile();
    }

    public boolean hasCredentials() {
        return clientKey != null && !clientKey.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getFrontendRedirect() {
        return frontendRedirect;
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
        account.setViewsCount(189400L);
        account.setVideosCount(36L);
        account.setAccountType("CREATOR");

        return socialAccountRepository.save(account);
    }

    public String buildAuthorizationUrl(UUID userId) {
        if (!hasCredentials()) {
            log.warn("TIKTOK_CLIENT_KEY is not configured.");
            return frontendRedirect + "?error=missing_credentials_tiktok";
        }

        String requestedScope = (scope != null && !scope.isBlank())
                ? scope.trim()
                : "user.info.basic,video.publish,video.upload";

        return "https://www.tiktok.com/v2/auth/authorize/?" +
                "client_key=" + URLEncoder.encode(clientKey, StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode(requestedScope, StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&state=" + (userId != null ? userId.toString() : "demo");
    }

    @Transactional
    public String handleCallback(String code, String state) {
        if (code == null || code.isBlank()) {
            return frontendRedirect + "?error=tiktok_missing_code";
        }
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
            if (tokenJson.has("error") && !tokenJson.path("error").asText("").isBlank()) {
                String errCode = tokenJson.path("error").asText("tiktok_oauth_error");
                String errDesc = tokenJson.path("error_description").asText(errCode);
                log.error("TikTok OAuth token exchange returned error: {} - {}", errCode, errDesc);
                return frontendRedirect + "?error=" + URLEncoder.encode(errDesc, StandardCharsets.UTF_8);
            }

            JsonNode dataNode = tokenJson.has("data") && tokenJson.get("data").isObject()
                    ? tokenJson.get("data")
                    : tokenJson;

            String accessToken = dataNode.path("access_token").asText("");
            String refreshToken = dataNode.path("refresh_token").asText("");
            long expiresIn = dataNode.path("expires_in").asLong(86400);

            if (accessToken.isBlank()) {
                log.error("TikTok token response missing access_token: {}", tokenRes.body());
                return frontendRedirect + "?error=tiktok_empty_access_token";
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

            account.setAccessToken(accessToken);
            if (!refreshToken.isBlank()) {
                account.setRefreshToken(refreshToken);
            }
            account.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
            account.setStatus(AccountStatus.CONNECTED);
            account.setAccountType("CREATOR");

            populateUserProfile(account, accessToken);

            socialAccountRepository.save(account);
            log.info("Successfully linked TikTok account '{}' to user {}", account.getChannelName(), user.getEmail());

            return frontendRedirect + "?connected=TIKTOK";
        } catch (Exception e) {
            log.error("Error processing TikTok OAuth callback", e);
            return frontendRedirect + "?error=tiktok_callback_exception";
        }
    }

    private void populateUserProfile(SocialAccount account, String accessToken) {
        String channelName = account.getChannelName() != null && !account.getChannelName().isBlank()
                ? account.getChannelName()
                : "@tiktok_creator";
        String avatarUrl = account.getAvatarUrl() != null ? account.getAvatarUrl() : "";
        long followersCount = account.getFollowersCount() != null ? account.getFollowersCount() : 0L;
        long likesCount = account.getViewsCount() != null ? account.getViewsCount() : 0L;
        long videoCount = account.getVideosCount() != null ? account.getVideosCount() : 0L;

        try {
            HttpRequest statsReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/user/info/?fields=open_id,union_id,avatar_url,display_name,follower_count,likes_count,video_count"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> statsRes = httpClient.send(statsReq, HttpResponse.BodyHandlers.ofString());
            boolean parsed = false;

            if (statsRes.statusCode() == 200) {
                JsonNode userJson = objectMapper.readTree(statsRes.body());
                JsonNode userData = userJson.path("data").path("user");
                if (userData.isObject() && !userData.isEmpty()) {
                    String displayName = userData.path("display_name").asText("").trim();
                    if (!displayName.isBlank()) {
                        channelName = displayName.startsWith("@") ? displayName : "@" + displayName;
                    }
                    avatarUrl = userData.path("avatar_url").asText(avatarUrl);
                    followersCount = userData.path("follower_count").asLong(followersCount);
                    likesCount = userData.path("likes_count").asLong(likesCount);
                    videoCount = userData.path("video_count").asLong(videoCount);
                    parsed = true;
                }
            }

            if (!parsed) {
                HttpRequest basicReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://open.tiktokapis.com/v2/user/info/?fields=open_id,union_id,avatar_url,display_name"))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();

                HttpResponse<String> basicRes = httpClient.send(basicReq, HttpResponse.BodyHandlers.ofString());
                if (basicRes.statusCode() == 200) {
                    JsonNode userJson = objectMapper.readTree(basicRes.body());
                    JsonNode userData = userJson.path("data").path("user");
                    if (userData.isObject() && !userData.isEmpty()) {
                        String displayName = userData.path("display_name").asText("").trim();
                        if (!displayName.isBlank()) {
                            channelName = displayName.startsWith("@") ? displayName : "@" + displayName;
                        }
                        avatarUrl = userData.path("avatar_url").asText(avatarUrl);
                    }
                } else {
                    log.warn("Could not query TikTok basic user profile: {}", basicRes.body());
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching TikTok user profile: {}", e.getMessage());
        }

        account.setChannelName(channelName);
        account.setAvatarUrl(avatarUrl);
        account.setFollowersCount(followersCount);
        account.setViewsCount(likesCount);
        account.setVideosCount(videoCount);
    }

    @Transactional
    public SocialAccount syncAccountStatistics(SocialAccount account) {
        if (account == null || account.getPlatform() != Platform.TIKTOK) {
            return account;
        }
        String token = account.getAccessToken();
        if (token == null || token.isBlank() || token.startsWith("demo_")) {
            return account;
        }
        if (account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isBefore(Instant.now().plusSeconds(60))) {
            token = refreshAccessToken(account);
        }
        populateUserProfile(account, token);
        account.setStatus(AccountStatus.CONNECTED);
        return socialAccountRepository.save(account);
    }

    @Transactional
    public String refreshAccessToken(SocialAccount account) {
        if (account == null || account.getRefreshToken() == null || account.getRefreshToken().isBlank()
                || account.getRefreshToken().startsWith("demo_")) {
            return account != null ? account.getAccessToken() : "";
        }
        try {
            String requestBody = "client_key=" + URLEncoder.encode(clientKey, StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                    "&grant_type=refresh_token" +
                    "&refresh_token=" + URLEncoder.encode(account.getRefreshToken(), StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/oauth/token/"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(res.body());
                JsonNode dataNode = json.has("data") && json.get("data").isObject() ? json.get("data") : json;
                String newAccess = dataNode.path("access_token").asText("");
                String newRefresh = dataNode.path("refresh_token").asText("");
                long expiresIn = dataNode.path("expires_in").asLong(86400);
                if (!newAccess.isBlank()) {
                    account.setAccessToken(newAccess);
                    if (!newRefresh.isBlank()) {
                        account.setRefreshToken(newRefresh);
                    }
                    account.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
                    account.setStatus(AccountStatus.CONNECTED);
                    socialAccountRepository.save(account);
                    return newAccess;
                }
            }
            log.warn("Could not refresh TikTok token: {}", res.body());
        } catch (Exception e) {
            log.warn("Error refreshing TikTok token: {}", e.getMessage());
        }
        return account.getAccessToken();
    }
}
