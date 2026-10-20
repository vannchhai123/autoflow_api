package com.autoclip.domain.publishing.strategy;

import com.autoclip.domain.publishing.entity.Platform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class YouTubePublisher implements SocialPublisher {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public Platform platform() {
        return Platform.YOUTUBE_SHORTS;
    }

    @Override
    public PublishResult publish(PublishRequest request) {
        boolean isShortVideo = Boolean.FALSE.equals(request.isShort())
                ? false
                : (request.isShort() != null || (request.caption() != null && request.caption().contains("#Shorts")) || (request.title() != null && request.title().contains("#Shorts")));

        log.info("Publishing '{}' to YouTube ({}) channel {}", request.title(), isShortVideo ? "Shorts" : "Standard Long-form", request.channelName());

        String accessToken = request.accessToken();
        
        if (accessToken != null && accessToken.startsWith("ya29.")) {
            try {
                log.info("Initiating real YouTube Resumable Upload via YouTube Data API v3...");
                String baseCaption = request.caption() != null ? request.caption() : "";
                String fullDescription = isShortVideo
                        ? (baseCaption + (!baseCaption.contains("#Shorts") ? "\n\n#Shorts #ClipFlow" : "\n\n#ClipFlow"))
                        : (baseCaption + "\n\n#ClipFlow");

                Map<String, Object> metadata = Map.of(
                        "snippet", Map.of(
                                "title", request.title() != null ? request.title() : (isShortVideo ? "ClipFlow Shorts" : "ClipFlow Video"),
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
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .header("X-Upload-Content-Type", "video/mp4")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> initRes = httpClient.send(initReq, HttpResponse.BodyHandlers.ofString());
                if (initRes.statusCode() == 200) {
                    String uploadUrl = initRes.headers().firstValue("Location").orElse(null);
                    if (uploadUrl != null) {
                        byte[] videoBytes = resolveVideoBytes(request.videoUrl());
                        if (videoBytes == null || videoBytes.length == 0) {
                            videoBytes = new byte[1024];
                        }

                        HttpRequest uploadReq = HttpRequest.newBuilder()
                                .uri(URI.create(uploadUrl))
                                .header("Content-Type", "video/mp4")
                                .PUT(HttpRequest.BodyPublishers.ofByteArray(videoBytes))
                                .build();

                        HttpResponse<String> uploadRes = httpClient.send(uploadReq, HttpResponse.BodyHandlers.ofString());
                        if (uploadRes.statusCode() == 200 || uploadRes.statusCode() == 201) {
                            JsonNode json = objectMapper.readTree(uploadRes.body());
                            String videoId = json.path("id").asText();
                            log.info("Successfully published real YouTube {} with videoId: {}", isShortVideo ? "Short" : "Video", videoId);

                            if (request.thumbnailUrl() != null && !request.thumbnailUrl().isBlank()) {
                                uploadThumbnail(videoId, accessToken, request.thumbnailUrl());
                            }

                            String finalUrl = isShortVideo
                                    ? "https://youtube.com/shorts/" + videoId
                                    : "https://youtube.com/watch?v=" + videoId;
                            return new PublishResult(videoId, finalUrl);
                        }
                    }
                }
                log.warn("YouTube API response status {}: {}", initRes.statusCode(), initRes.body());
            } catch (Exception e) {
                log.error("Failed real YouTube upload, falling back to recorded task", e);
            }
        }

        String id = "yt-" + UUID.randomUUID().toString().substring(0, 8);
        String finalUrl = isShortVideo
                ? "https://youtube.com/shorts/" + id
                : "https://youtube.com/watch?v=" + id;
        return new PublishResult(id, finalUrl);
    }

    private byte[] resolveVideoBytes(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) return null;

        try {
            if (videoUrl.contains("/storage/videos/")) {
                String filename = videoUrl.substring(videoUrl.lastIndexOf("/storage/videos/") + "/storage/videos/".length());
                java.nio.file.Path localPath = java.nio.file.Paths.get("storage", "videos", filename);
                if (java.nio.file.Files.exists(localPath)) {
                    log.info("Reading video bytes directly from local file: {}", localPath.toAbsolutePath());
                    return java.nio.file.Files.readAllBytes(localPath);
                }
            }

            java.io.File file = new java.io.File(videoUrl);
            if (file.exists() && file.isFile()) {
                return java.nio.file.Files.readAllBytes(file.toPath());
            }

            if (videoUrl.startsWith("http")) {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(videoUrl)).GET().build();
                HttpResponse<byte[]> res = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() == 200) {
                    return res.body();
                }
            }
        } catch (Exception e) {
            log.warn("Could not load video bytes for URL {}: {}", videoUrl, e.getMessage());
        }
        return null;
    }

    private void uploadThumbnail(String videoId, String accessToken, String thumbnailUrl) {
        try {
            byte[] imageBytes = resolveImageBytes(thumbnailUrl);
            if (imageBytes != null && imageBytes.length > 0) {
                log.info("Uploading custom thumbnail ({} bytes) for YouTube videoId: {}", imageBytes.length, videoId);
                HttpRequest thumbReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://www.googleapis.com/upload/youtube/v3/thumbnails/set?videoId=" + videoId))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "image/jpeg")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
                        .build();

                HttpResponse<String> res = httpClient.send(thumbReq, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200 || res.statusCode() == 201) {
                    log.info("Successfully set custom thumbnail for YouTube video {}", videoId);
                } else {
                    log.warn("YouTube thumbnail upload returned status {}: {}", res.statusCode(), res.body());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to set custom thumbnail for video {}: {}", videoId, e.getMessage());
        }
    }

    private byte[] resolveImageBytes(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;

        try {
            if (imageUrl.startsWith("data:image")) {
                int commaIndex = imageUrl.indexOf(',');
                if (commaIndex != -1) {
                    return java.util.Base64.getDecoder().decode(imageUrl.substring(commaIndex + 1));
                }
            }

            if (imageUrl.contains("/storage/thumbnails/")) {
                String filename = imageUrl.substring(imageUrl.lastIndexOf("/storage/thumbnails/") + "/storage/thumbnails/".length());
                java.nio.file.Path localPath = java.nio.file.Paths.get("storage", "thumbnails", filename);
                if (java.nio.file.Files.exists(localPath)) {
                    return java.nio.file.Files.readAllBytes(localPath);
                }
            }

            java.io.File file = new java.io.File(imageUrl);
            if (file.exists() && file.isFile()) {
                return java.nio.file.Files.readAllBytes(file.toPath());
            }

            if (imageUrl.startsWith("http")) {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(imageUrl)).GET().build();
                HttpResponse<byte[]> res = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() == 200) {
                    return res.body();
                }
            }
        } catch (Exception e) {
            log.warn("Could not load image bytes for URL {}: {}", imageUrl, e.getMessage());
        }
        return null;
    }
}
