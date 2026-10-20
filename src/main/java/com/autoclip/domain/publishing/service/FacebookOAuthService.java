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
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacebookOAuthService {

    @Value("${clipflow.oauth.facebook.app-id:}")
    private String appId;

    @Value("${clipflow.oauth.facebook.app-secret:}")
    private String appSecret;

    @Value("${clipflow.oauth.facebook.redirect-uri:http://localhost:8090/api/accounts/oauth2/callback/facebook}")
    private String redirectUri;

    @Value("${clipflow.oauth.facebook.frontend-redirect:http://localhost:3000/settings/accounts}")
    private String frontendRedirect;

    @Value("${clipflow.oauth.facebook.scope:public_profile}")
    private String configuredScope;

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
            redirectUri = "http://localhost:8090/api/accounts/oauth2/callback/facebook";
        }
        if (frontendRedirect == null || frontendRedirect.isBlank()) {
            frontendRedirect = "http://localhost:3000/settings/accounts";
        }
    }

    private User resolveDefaultUser() {
        if (adminEmail != null && !adminEmail.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmail(adminEmail.trim());
            if (byEmail.isPresent()) return byEmail.get();
        }
        return userRepository.findAll().stream().findFirst().orElse(null);
    }

    public String buildAuthorizationUrl(UUID userId) {
        if (appId == null || appId.isBlank()) {
            log.warn("FACEBOOK_APP_ID is not configured.");
            return frontendRedirect + "?error=missing_credentials_facebook";
        }

        String scope = (configuredScope != null && !configuredScope.isBlank()) ? configuredScope : "public_profile,email";

        return "https://www.facebook.com/v19.0/dialog/oauth?" +
                "client_id=" + URLEncoder.encode(appId, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&state=" + (userId != null ? userId.toString() : "demo");
    }

    @Transactional
    public String handleCallback(String code, String state) {
        try {
            log.info("Handling Facebook OAuth callback with code...");

            String tokenUrl = "https://graph.facebook.com/v19.0/oauth/access_token?" +
                    "client_id=" + URLEncoder.encode(appId, StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(appSecret, StandardCharsets.UTF_8) +
                    "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                    "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);

            HttpRequest tokenReq = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .GET()
                    .build();

            HttpResponse<String> tokenRes = httpClient.send(tokenReq, HttpResponse.BodyHandlers.ofString());

            if (tokenRes.statusCode() != 200) {
                log.error("Failed to exchange Facebook code for token. HTTP {} -> {}", tokenRes.statusCode(), tokenRes.body());
                return frontendRedirect + "?error=facebook_token_exchange_failed";
            }

            JsonNode tokenJson = objectMapper.readTree(tokenRes.body());
            String userAccessToken = tokenJson.path("access_token").asText();
            long expiresIn = tokenJson.path("expires_in").asLong(60 * 86400);

            HttpRequest pagesReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.facebook.com/v19.0/me/accounts?fields=id,name,access_token,followers_count,picture&access_token=" + userAccessToken))
                    .GET()
                    .build();

            HttpResponse<String> pagesRes = httpClient.send(pagesReq, HttpResponse.BodyHandlers.ofString());

            String pageName = "Facebook Page";
            String pageAccessToken = userAccessToken;
            String avatarUrl = "";
            long followersCount = 0;

            if (pagesRes.statusCode() == 200) {
                JsonNode pagesJson = objectMapper.readTree(pagesRes.body());
                JsonNode dataArr = pagesJson.path("data");
                if (dataArr.isArray() && !dataArr.isEmpty()) {
                    JsonNode page = dataArr.get(0);
                    pageName = page.path("name").asText("Facebook Page");
                    pageAccessToken = page.path("access_token").asText(userAccessToken);
                    followersCount = page.path("followers_count").asLong(0);
                    avatarUrl = page.path("picture").path("data").path("url").asText("");
                }
            } else {
                log.info("Facebook pages query returned status {}. Falling back to user profile.", pagesRes.statusCode());
            }

            if ("Facebook Page".equals(pageName)) {
                try {
                    HttpRequest meReq = HttpRequest.newBuilder()
                            .uri(URI.create("https://graph.facebook.com/v19.0/me?fields=name,picture&access_token=" + userAccessToken))
                            .GET()
                            .build();
                    HttpResponse<String> meRes = httpClient.send(meReq, HttpResponse.BodyHandlers.ofString());
                    if (meRes.statusCode() == 200) {
                        JsonNode meJson = objectMapper.readTree(meRes.body());
                        pageName = meJson.path("name").asText("Facebook Creator");
                        avatarUrl = meJson.path("picture").path("data").path("url").asText("");
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch Facebook user profile: {}", e.getMessage());
                }
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
                log.error("No valid user found to bind Facebook channel");
                return frontendRedirect + "?error=user_not_found";
            }

            User targetUser = user;
            SocialAccount account = socialAccountRepository.findFirstByOwnerIdAndPlatform(targetUser.getId(), Platform.FACEBOOK)
                    .orElseGet(() -> {
                        SocialAccount a = new SocialAccount();
                        a.setOwner(targetUser);
                        a.setPlatform(Platform.FACEBOOK);
                        return a;
                    });

            account.setChannelName(pageName);
            account.setAvatarUrl(avatarUrl);
            account.setAccessToken(pageAccessToken);
            account.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
            account.setStatus(AccountStatus.CONNECTED);
            account.setFollowersCount(followersCount);
            account.setAccountType("COMPANY_PAGE");

            socialAccountRepository.save(account);
            log.info("Successfully linked Facebook page '{}' to user {}", pageName, user.getEmail());

            return frontendRedirect + "?connected=FACEBOOK";
        } catch (Exception e) {
            log.error("Error processing Facebook OAuth callback", e);
            return frontendRedirect + "?error=facebook_callback_exception";
        }
    }
}
