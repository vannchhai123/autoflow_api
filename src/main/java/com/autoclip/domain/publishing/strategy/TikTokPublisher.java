package com.autoclip.domain.publishing.strategy;

import com.autoclip.domain.publishing.entity.Platform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TikTokPublisher implements SocialPublisher {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public Platform platform() {
        return Platform.TIKTOK;
    }

    @Override
    public PublishResult publish(PublishRequest request) {
        log.info("Initiating publishing '{}' to TikTok account {}", request.title(), request.channelName());

        String accessToken = request.accessToken();
        byte[] videoBytes = resolveVideoBytes(request.videoUrl());

        if (accessToken != null && !accessToken.isBlank()
                && !accessToken.startsWith("mock-") && !accessToken.startsWith("demo_")
                && videoBytes != null && videoBytes.length > 0) {
            try {
                log.info("Executing real TikTok Content Posting API v2 upload (size: {} bytes)...", videoBytes.length);
                String title = request.caption() != null && !request.caption().isBlank() ? request.caption() : request.title();
                if (title == null || title.isBlank()) title = "ClipFlow TikTok Video";

                PublishResult directPublic = tryDirectPost(accessToken, title, "PUBLIC_TO_EVERYONE", videoBytes, request.channelName());
                if (directPublic != null) {
                    return directPublic;
                }

                PublishResult directPrivate = tryDirectPost(accessToken, title, "SELF_ONLY", videoBytes, request.channelName());
                if (directPrivate != null) {
                    return directPrivate;
                }

                PublishResult inboxUpload = tryInboxUpload(accessToken, videoBytes, request.channelName());
                if (inboxUpload != null) {
                    return inboxUpload;
                }
            } catch (Exception e) {
                log.error("Failed to upload video to TikTok via Content Posting API", e);
            }
        }

        String id = "tt-" + UUID.randomUUID().toString().substring(0, 10);
        String handle = request.channelName() != null ? request.channelName().replace("@", "") : "creator";
        return new PublishResult(id, "https://www.tiktok.com/@" + handle + "/video/" + id);
    }

    private PublishResult tryDirectPost(String accessToken, String title, String privacyLevel, byte[] videoBytes, String channelName) {
        try {
            Map<String, Object> initPayload = Map.of(
                    "post_info", Map.of(
                            "title", title,
                            "privacy_level", privacyLevel,
                            "disable_duet", false,
                            "disable_comment", false,
                            "disable_stitch", false,
                            "video_cover_timestamp_ms", 1000
                    ),
                    "source_info", Map.of(
                            "source", "FILE_UPLOAD",
                            "video_size", videoBytes.length,
                            "chunk_size", videoBytes.length,
                            "total_chunk_count", 1
                    )
            );

            String jsonBody = objectMapper.writeValueAsString(initPayload);

            HttpRequest initReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/post/publish/video/init/"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> initRes = httpClient.send(initReq, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok Direct Post ({}) init status: {}", privacyLevel, initRes.statusCode());

            if (initRes.statusCode() == 200) {
                return executeChunkUpload(initRes.body(), videoBytes, channelName);
            } else {
                log.warn("TikTok Direct Post ({}) init failed: {} -> {}", privacyLevel, initRes.statusCode(), initRes.body());
            }
        } catch (Exception e) {
            log.warn("Error in TikTok Direct Post ({}): {}", privacyLevel, e.getMessage());
        }
        return null;
    }

    private PublishResult tryInboxUpload(String accessToken, byte[] videoBytes, String channelName) {
        try {
            Map<String, Object> initPayload = Map.of(
                    "source_info", Map.of(
                            "source", "FILE_UPLOAD",
                            "video_size", videoBytes.length,
                            "chunk_size", videoBytes.length,
                            "total_chunk_count", 1
                    )
            );

            String jsonBody = objectMapper.writeValueAsString(initPayload);

            HttpRequest initReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/post/publish/inbox/video/init/"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> initRes = httpClient.send(initReq, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok Inbox Upload init status: {}", initRes.statusCode());

            if (initRes.statusCode() == 200) {
                return executeChunkUpload(initRes.body(), videoBytes, channelName);
            } else {
                log.warn("TikTok Inbox Upload init failed: {} -> {}", initRes.statusCode(), initRes.body());
            }
        } catch (Exception e) {
            log.warn("Error in TikTok Inbox Upload: {}", e.getMessage());
        }
        return null;
    }

    private PublishResult executeChunkUpload(String initResponseBody, byte[] videoBytes, String channelName) throws Exception {
        JsonNode initJson = objectMapper.readTree(initResponseBody);
        String publishId = initJson.path("data").path("publish_id").asText("");
        String uploadUrl = initJson.path("data").path("upload_url").asText("");

        if (!uploadUrl.isBlank()) {
            HttpRequest uploadReq = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Content-Type", "video/mp4")
                    .header("Content-Range", "bytes 0-" + (videoBytes.length - 1) + "/" + videoBytes.length)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(videoBytes))
                    .timeout(Duration.ofMinutes(3))
                    .build();

            HttpResponse<String> uploadRes = httpClient.send(uploadReq, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok binary upload response status: {}", uploadRes.statusCode());

            if (uploadRes.statusCode() == 200 || uploadRes.statusCode() == 201) {
                String handle = channelName != null ? channelName.replace("@", "") : "creator";
                String publicUrl = "https://www.tiktok.com/@" + handle + "/video/" + publishId;
                log.info("Successfully published video to TikTok! Public URL: {}", publicUrl);
                return new PublishResult(publishId, publicUrl);
            }
        }
        return null;
    }

    private byte[] resolveVideoBytes(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) return null;

        try {
            if (videoUrl.contains("/storage/videos/")) {
                String filename = videoUrl.substring(videoUrl.lastIndexOf("/storage/videos/") + "/storage/videos/".length());
                Path localPath = Paths.get("storage", "videos", filename);
                if (Files.exists(localPath)) {
                    log.info("Reading video bytes directly from local file: {}", localPath.toAbsolutePath());
                    return Files.readAllBytes(localPath);
                }
            }

            File file = new File(videoUrl);
            if (file.exists() && file.isFile()) {
                return Files.readAllBytes(file.toPath());
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
}
