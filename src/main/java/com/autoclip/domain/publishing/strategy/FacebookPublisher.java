package com.autoclip.domain.publishing.strategy;

import com.autoclip.domain.publishing.entity.Platform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacebookPublisher implements SocialPublisher {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public Platform platform() {
        return Platform.FACEBOOK;
    }

    @Override
    public PublishResult publish(PublishRequest request) {
        log.info("Initiating publishing '{}' to Facebook Channel {}", request.title(), request.channelName());

        String accessToken = request.accessToken();
        byte[] videoBytes = resolveVideoBytes(request.videoUrl());

        if (accessToken != null && !accessToken.isBlank() && !accessToken.startsWith("mock-") && videoBytes != null && videoBytes.length > 0) {
            try {
                log.info("Executing real Facebook Graph Video API upload (size: {} bytes)...", videoBytes.length);
                String caption = request.caption() != null ? request.caption() : request.title();
                String title = request.title() != null ? request.title() : "ClipFlow Video";

                String boundary = "----ClipFlowBoundary" + UUID.randomUUID().toString().replace("-", "");
                byte[] multipartBody = buildMultipartBody(boundary, title, caption, videoBytes);

                HttpRequest postReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://graph-video.facebook.com/v19.0/me/videos"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                        .timeout(Duration.ofMinutes(3))
                        .build();

                HttpResponse<String> response = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
                log.info("Facebook Graph API video upload response status: {}", response.statusCode());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    JsonNode json = objectMapper.readTree(response.body());
                    String videoId = json.path("id").asText();
                    if (!videoId.isBlank()) {
                        log.info("Successfully published video to Facebook with ID: {}", videoId);
                        return new PublishResult(videoId, "https://facebook.com/watch/?v=" + videoId);
                    }
                }

                log.error("Facebook Video API upload failed with status {}: {}", response.statusCode(), response.body());
                JsonNode errJson = objectMapper.readTree(response.body());
                String errMsg = errJson.path("error").path("message").asText("API access blocked");
                throw new IllegalStateException("Facebook rejected the upload: " + errMsg + ". Note: Facebook Graph API only allows automated video publishing to Facebook Pages, not personal user profiles.");
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to upload video to Facebook via Graph API", e);
                throw new RuntimeException("Failed to upload video to Facebook: " + e.getMessage(), e);
            }
        }

        if (accessToken == null || accessToken.isBlank() || accessToken.startsWith("mock-")) {
            String id = "fb-" + UUID.randomUUID().toString().substring(0, 10);
            return new PublishResult(id, "https://facebook.com/watch/?v=" + id);
        }

        throw new IllegalStateException("Cannot publish to Facebook: missing valid access token or video media bytes.");
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

    private byte[] buildMultipartBody(String boundary, String title, String description, byte[] videoBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String lineBreak = "\r\n";

        baos.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"title\"" + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write((title + lineBreak).getBytes(StandardCharsets.UTF_8));

        baos.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"description\"" + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write((description + lineBreak).getBytes(StandardCharsets.UTF_8));

        baos.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"source\"; filename=\"clipflow_video.mp4\"" + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Type: video/mp4" + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(videoBytes);
        baos.write(lineBreak.getBytes(StandardCharsets.UTF_8));

        baos.write(("--" + boundary + "--" + lineBreak).getBytes(StandardCharsets.UTF_8));

        return baos.toByteArray();
    }
}
