package com.autoclip.domain.video.service;

import com.autoclip.domain.video.entity.VideoProject;
import com.autoclip.domain.video.entity.VideoRenderJob;
import com.autoclip.domain.video.repository.ClonedVoiceRepository;
import com.autoclip.domain.video.repository.VideoRenderJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoEngineService {

    private final VideoRenderJobRepository jobRepository;
    private final ClonedVoiceRepository clonedVoiceRepository;

    @Value("${autoclip.storage.base-url:}")
    private String storageBaseUrl;

    @Value("${server.port:8080}")
    private String serverPort;

    private String resolvePythonExecutable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            if (new File("/usr/bin/python3").exists()) {
                return "/usr/bin/python3";
            }
            return "python3";
        }
        return "python";
    }

    public String render(VideoProject project, VideoRenderJob job) throws Exception {
        String videoFileName = project.getId().toString() + ".mp4";
        Path outputPath = Paths.get("storage", "videos", videoFileName).toAbsolutePath();
        File outputFile = outputPath.toFile();
        outputFile.getParentFile().mkdirs();

        String scriptPath = Paths.get("scripts", "render_engine.py").toAbsolutePath().toString();
        String scriptText = (project.getScriptText() != null && !project.getScriptText().isBlank())
                ? project.getScriptText()
                : project.getTitle();

        String voiceModel = (project.getVoice() != null && !project.getVoice().isBlank())
                ? project.getVoice()
                : "en-US-ChristopherNeural";

        String pythonExec = resolvePythonExecutable();

        List<String> command = new ArrayList<>(List.of(
                pythonExec,
                scriptPath,
                "--title", project.getTitle(),
                "--text", scriptText,
                "--aspect_ratio", project.getAspectRatio().name(),
                "--output", outputPath.toString()
        ));

        if (project.getAudioUrl() != null && !project.getAudioUrl().isBlank()) {
            String audioUrl = project.getAudioUrl();
            String filename = audioUrl.contains("/") ? audioUrl.substring(audioUrl.lastIndexOf('/') + 1) : audioUrl;
            Path audioPath = Paths.get("storage", "audio", filename).toAbsolutePath();
            if (audioPath.toFile().exists()) {
                log.info("Using custom uploaded audio file: {}", audioPath);
                command.add("--audio_file");
                command.add(audioPath.toString());
            } else {
                log.warn("Custom audio file not found at: {}. Falling back to TTS voice.", audioPath);
                command.add("--voice");
                command.add(voiceModel);
            }
        } else if (voiceModel.startsWith("cloned:")) {
            try {
                UUID voiceId = UUID.fromString(voiceModel.substring("cloned:".length()));
                clonedVoiceRepository.findById(voiceId).ifPresent(cv -> {
                    String sampleUrl = cv.getAudioSampleUrl();
                    String baseName = sampleUrl.substring(sampleUrl.lastIndexOf('/') + 1);
                    if (baseName.contains(".")) {
                        baseName = baseName.substring(0, baseName.lastIndexOf('.'));
                    }
                    Path profilePath = Paths.get("storage", "cloned_voices", baseName + ".json").toAbsolutePath();
                    if (!profilePath.toFile().exists() && cv.getProfileJson() != null) {
                        try {
                            Files.writeString(profilePath, cv.getProfileJson(), StandardCharsets.UTF_8);
                        } catch (Exception ignored) {}
                    }
                    if (profilePath.toFile().exists()) {
                        log.info("Using cloned voice profile: {}", profilePath);
                        command.add("--cloned_profile");
                        command.add(profilePath.toString());
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to resolve cloned voice: {}", voiceModel, e);
            }
            if (!command.contains("--cloned_profile")) {
                command.add("--voice");
                command.add("en-US-ChristopherNeural");
            }
        } else {
            command.add("--voice");
            command.add(voiceModel);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("PYTHONUTF8", "1");
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");

        processBuilder.redirectErrorStream(false);
        log.info("Starting video render process for project: {} using {}", project.getId(), pythonExec);

        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[RenderEngine] {}", line);
                if (line.startsWith("PROGRESS:")) {
                    String[] parts = line.split(":", 3);
                    if (parts.length >= 2) {
                        try {
                            int pct = Integer.parseInt(parts[1]);
                            job.setProgressPercentage(pct);
                            jobRepository.save(job);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String errLine;
                while ((errLine = errReader.readLine()) != null) {
                    errorOutput.append(errLine).append("\n");
                }
            }
            throw new IllegalStateException("Video rendering failed with code " + exitCode + ": " + errorOutput);
        }

        if (storageBaseUrl != null && !storageBaseUrl.isBlank()) {
            return storageBaseUrl.replaceAll("/+$", "") + "/storage/videos/" + videoFileName;
        }
        String renderUrl = System.getenv("RENDER_EXTERNAL_URL");
        if (renderUrl != null && !renderUrl.isBlank()) {
            return renderUrl.replaceAll("/+$", "") + "/storage/videos/" + videoFileName;
        }
        return "https://autoflow-api-f5c6.onrender.com/storage/videos/" + videoFileName;
    }
}
