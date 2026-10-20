package com.autoclip.domain.publishing.service;

import com.autoclip.domain.auth.entity.User;
import com.autoclip.domain.auth.repository.UserRepository;
import com.autoclip.domain.publishing.entity.AccountStatus;
import com.autoclip.domain.publishing.entity.Platform;
import com.autoclip.domain.publishing.entity.SocialAccount;
import com.autoclip.domain.publishing.repository.SocialAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
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
public class YouTubeOAuthService {

    @Value("${clipflow.oauth.google.client-id:}")
    private String clientId;

    @Value("${clipflow.oauth.google.client-secret:}")
    private String clientSecret;

    @Value("${clipflow.oauth.google.redirect-uri:http://localhost:8090/api/accounts/oauth2/callback/youtube}")
    private String redirectUri;

    @Value("${clipflow.oauth.google.frontend-redirect:http://localhost:3000/settings/accounts}")
    private String frontendRedirect;

    @Value("${autoclip.admin.email:}")
    private String adminEmail;

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @jakarta.annotation.PostConstruct
    public void init() {
        if (redirectUri == null || redirectUri.isBlank()) {
            redirectUri = "http://localhost:8090/api/accounts/oauth2/callback/youtube";
        }
        if (frontendRedirect == null || frontendRedirect.isBlank()) {
            frontendRedirect = "http://localhost:3000/settings/accounts";
        }
        loadSavedCredentials();
    }

    public synchronized void setCredentials(String newClientId, String newClientSecret) {
        this.clientId = (newClientId != null) ? newClientId.trim() : "";
        this.clientSecret = (newClientSecret != null) ? newClientSecret.trim() : "";
        saveCredentialsToFile();
    }

    public boolean hasCredentials() {
        return clientId != null && !clientId.isBlank() && clientId.endsWith(".apps.googleusercontent.com");
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
                JsonNode google = root.path("google");
                if (google.has("clientId") && (this.clientId == null || this.clientId.isBlank())) {
                    this.clientId = google.get("clientId").asText();
                }
                if (google.has("clientSecret") && (this.clientSecret == null || this.clientSecret.isBlank())) {
                    this.clientSecret = google.get("clientSecret").asText();
                }
                log.info("Loaded persisted Google OAuth credentials from storage/oauth_credentials.json (configured: {})", hasCredentials());
            }
        } catch (Exception e) {
            log.warn("Could not load credentials from storage: {}", e.getMessage());
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
            Map<String, String> googleMap = new java.util.HashMap<>();
            googleMap.put("clientId", this.clientId != null ? this.clientId : "");
            googleMap.put("clientSecret", this.clientSecret != null ? this.clientSecret : "");
            map.put("google", googleMap);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), map);
            log.info("Persisted Google OAuth credentials to storage/oauth_credentials.json");
        } catch (Exception e) {
            log.error("Failed to persist OAuth credentials: {}", e.getMessage());
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
        SocialAccount account = socialAccountRepository.findFirstByOwnerIdAndPlatform(targetUser.getId(), Platform.YOUTUBE_SHORTS)
                .orElseGet(() -> {
                    SocialAccount a = new SocialAccount();
                    a.setOwner(targetUser);
                    a.setPlatform(Platform.YOUTUBE_SHORTS);
                    return a;
                });

        account.setChannelName("AutoClip Shorts Official");
        account.setAvatarUrl("https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80");
        account.setAccessToken("demo_youtube_access_token_" + UUID.randomUUID());
        account.setRefreshToken("demo_youtube_refresh_token_" + UUID.randomUUID());
        account.setTokenExpiresAt(Instant.now().plusSeconds(60 * 86400));
        account.setStatus(AccountStatus.CONNECTED);
        account.setFollowersCount(14800L);
        account.setAccountType("CREATOR");

        return socialAccountRepository.save(account);
    }

    public String buildAuthorizationUrl(UUID userId) {
        if (!hasCredentials()) {
            log.warn("GOOGLE_CLIENT_ID is not configured or invalid (must end in .apps.googleusercontent.com). Falling back to demo OAuth consent screen.");
            return frontendRedirect + "?error=missing_credentials";
        }

        String scopes = "https://www.googleapis.com/auth/youtube.upload " +
                "https://www.googleapis.com/auth/youtube.readonly " +
                "https://www.googleapis.com/auth/userinfo.profile";

        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode(scopes, StandardCharsets.UTF_8) +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=" + (userId != null ? userId.toString() : "demo");
    }

    @Transactional
    public String handleCallback(String code, String state) {
        try {
            log.info("Handling YouTube OAuth callback with code...");

            String tokenRequestBody = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                    "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                    "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                    "&grant_type=authorization_code";

            HttpRequest tokenReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(tokenRequestBody))
                    .build();

            HttpResponse<String> tokenRes = httpClient.send(tokenReq, HttpResponse.BodyHandlers.ofString());

            if (tokenRes.statusCode() != 200) {
                log.error("Failed to exchange code for tokens. HTTP {} -> {}", tokenRes.statusCode(), tokenRes.body());
                return frontendRedirect + "?error=token_exchange_failed";
            }

            JsonNode tokenJson = objectMapper.readTree(tokenRes.body());
            String accessToken = tokenJson.path("access_token").asText();
            String refreshToken = tokenJson.path("refresh_token").asText(null);
            long expiresIn = tokenJson.path("expires_in").asLong(3600);

            HttpRequest channelReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&mine=true"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> channelRes = httpClient.send(channelReq, HttpResponse.BodyHandlers.ofString());

            String channelName = "YouTube Channel";
            String avatarUrl = "";
            long followersCount = 0;

            if (channelRes.statusCode() == 200) {
                JsonNode channelJson = objectMapper.readTree(channelRes.body());
                JsonNode items = channelJson.path("items");
                if (items.isArray() && !items.isEmpty()) {
                    JsonNode channel = items.get(0);
                    channelName = channel.path("snippet").path("title").asText("YouTube Channel");
                    avatarUrl = channel.path("snippet").path("thumbnails").path("default").path("url").asText("");
                    followersCount = channel.path("statistics").path("subscriberCount").asLong(0);
                }
            } else {
                log.warn("Could not query YouTube channel profile: {}", channelRes.body());
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
                log.error("No valid user found to bind YouTube channel");
                return frontendRedirect + "?error=user_not_found";
            }

            User targetUser = user;
            SocialAccount account = socialAccountRepository.findFirstByOwnerIdAndPlatform(targetUser.getId(), Platform.YOUTUBE_SHORTS)
                    .orElseGet(() -> {
                        SocialAccount a = new SocialAccount();
                        a.setOwner(targetUser);
                        a.setPlatform(Platform.YOUTUBE_SHORTS);
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
            account.setAccountType("CREATOR");

            socialAccountRepository.save(account);
            log.info("Successfully linked YouTube channel '{}' ({}) to user {}", channelName, account.getId(), user.getEmail());

            return frontendRedirect + "?connected=YOUTUBE_SHORTS";
        } catch (Exception e) {
            log.error("Exception handling YouTube OAuth callback", e);
            return frontendRedirect + "?error=oauth_exception";
        }
    }

    public String refreshAccessToken(SocialAccount account) {
        if (account.getRefreshToken() == null || account.getRefreshToken().isBlank()) {
            log.warn("No refresh token available for YouTube account {}", account.getId());
            return account.getAccessToken();
        }

        try {
            String refreshBody = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                    "&refresh_token=" + URLEncoder.encode(account.getRefreshToken(), StandardCharsets.UTF_8) +
                    "&grant_type=refresh_token";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(refreshBody))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(res.body());
                String newAccess = json.path("access_token").asText();
                long expiresIn = json.path("expires_in").asLong(3600);
                account.setAccessToken(newAccess);
                account.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
                account.setStatus(AccountStatus.CONNECTED);
                socialAccountRepository.save(account);
                return newAccess;
            } else {
                log.error("Failed to refresh token: {}", res.body());
                account.setStatus(AccountStatus.NEEDS_RECONNECT);
                socialAccountRepository.save(account);
            }
        } catch (Exception e) {
            log.error("Error refreshing token for account {}", account.getId(), e);
        }
        return account.getAccessToken();
    }

    public String uploadShorts(SocialAccount account, String title, String caption, String videoUrl) throws Exception {
        String token = account.getAccessToken();
        if (account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isBefore(Instant.now().plusSeconds(60))) {
            token = refreshAccessToken(account);
        }

        String fullDescription = (caption != null ? caption : "") + "\n\n#Shorts #ClipFlow";

        Map<String, Object> metadata = Map.of(
                "snippet", Map.of(
                        "title", title != null ? title : "ClipFlow Video",
                        "description", fullDescription,
                        "categoryId", "22"
                ),
                "status", Map.of(
                        "privacyStatus", "public",
                        "selfDeclaredMadeForKids", false
                )
        );

        String jsonPayload = objectMapper.writeValueAsString(metadata);

        HttpRequest initReq = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type", "video/mp4")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> initRes = httpClient.send(initReq, HttpResponse.BodyHandlers.ofString());

        if (initRes.statusCode() != 200) {
            throw new IllegalStateException("Failed to initiate YouTube upload session. HTTP " + initRes.statusCode() + ": " + initRes.body());
        }

        String uploadLocation = initRes.headers().firstValue("Location")
                .orElseThrow(() -> new IllegalStateException("Missing Location header from YouTube upload session"));

        log.info("Acquired YouTube resumable upload session: {}", uploadLocation);

        byte[] videoBytes;
        if (videoUrl != null && videoUrl.startsWith("http")) {
            HttpRequest fetchVideo = HttpRequest.newBuilder().uri(URI.create(videoUrl)).GET().build();
            HttpResponse<byte[]> videoRes = httpClient.send(fetchVideo, HttpResponse.BodyHandlers.ofByteArray());
            videoBytes = videoRes.body();
        } else {
            
            videoBytes = new byte[1024];
        }

        HttpRequest uploadReq = HttpRequest.newBuilder()
                .uri(URI.create(uploadLocation))
                .header("Content-Type", "video/mp4")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(videoBytes))
                .build();

        HttpResponse<String> uploadRes = httpClient.send(uploadReq, HttpResponse.BodyHandlers.ofString());

        if (uploadRes.statusCode() == 200 || uploadRes.statusCode() == 201) {
            JsonNode uploaded = objectMapper.readTree(uploadRes.body());
            String videoId = uploaded.path("id").asText();
            log.info("YouTube video published successfully! Video ID: {}", videoId);
            return videoId;
        } else {
            throw new IllegalStateException("Failed to upload video bytes to YouTube: " + uploadRes.body());
        }
    }
}
