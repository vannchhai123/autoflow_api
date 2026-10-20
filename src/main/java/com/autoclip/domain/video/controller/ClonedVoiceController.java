package com.autoclip.domain.video.controller;

import com.autoclip.common.exception.BusinessException;
import com.autoclip.common.exception.ResourceNotFoundException;
import com.autoclip.domain.auth.entity.User;
import com.autoclip.domain.video.dto.VideoDtos.ClonedVoiceResponse;
import com.autoclip.domain.video.entity.ClonedVoice;
import com.autoclip.domain.video.repository.ClonedVoiceRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Slf4j
@Tag(name = "Cloned Voices")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/voices")
@RequiredArgsConstructor
public class ClonedVoiceController {

    private final ClonedVoiceRepository voiceRepository;

    @GetMapping("/cloned")
    @Transactional(readOnly = true)
    public List<ClonedVoiceResponse> list(@AuthenticationPrincipal User user) {
        return voiceRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(ClonedVoiceResponse::from)
                .toList();
    }

    @PostMapping(value = "/clone", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ClonedVoiceResponse cloneVoice(
            @AuthenticationPrincipal User user,
            @RequestParam("name") String name,
            @RequestParam(value = "baseVoice", defaultValue = "km-KH-PisethNeural") String baseVoice,
            @RequestParam("file") MultipartFile file) throws Exception {

        if (name == null || name.isBlank()) {
            throw new BusinessException("Voice name is required", HttpStatus.BAD_REQUEST);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Audio file is required for voice cloning", HttpStatus.BAD_REQUEST);
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.lastIndexOf('.') > 0) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        }
        if (!List.of(".mp3", ".wav", ".m4a", ".ogg", ".aac", ".flac").contains(ext)) {
            throw new BusinessException("Unsupported audio format. Allowed: .mp3, .wav, .m4a, .ogg, .aac, .flac", HttpStatus.BAD_REQUEST);
        }

        String voiceFileId = UUID.randomUUID().toString();
        Path voiceDir = Paths.get("storage", "cloned_voices").toAbsolutePath();
        Files.createDirectories(voiceDir);
        Path audioPath = voiceDir.resolve(voiceFileId + ext);
        Files.copy(file.getInputStream(), audioPath, StandardCopyOption.REPLACE_EXISTING);

        String scriptPath = Paths.get("scripts", "voice_cloner.py").toAbsolutePath().toString();
        ProcessBuilder pb = new ProcessBuilder(
                "python",
                scriptPath,
                "--analyze", audioPath.toString(),
                "--base_voice", baseVoice
        );
        pb.redirectErrorStream(false);
        Process process = pb.start();

        StringBuilder jsonOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonOutput.append(line).append("\n");
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("Voice analysis failed with code {}", exitCode);
            throw new BusinessException("Failed to analyze voice recording. Ensure the audio is audible speech.", HttpStatus.BAD_REQUEST);
        }

        String profileJson = jsonOutput.toString().trim();

        Path profilePath = voiceDir.resolve(voiceFileId + ".json");
        Files.writeString(profilePath, profileJson, StandardCharsets.UTF_8);

        ClonedVoice voice = new ClonedVoice();
        voice.setOwner(user);
        voice.setName(name.trim());
        voice.setBaseVoice(baseVoice);
        voice.setAudioSampleUrl("/storage/cloned_voices/" + voiceFileId + ext);
        voice.setProfileJson(profileJson);

        ClonedVoice saved = voiceRepository.save(voice);
        log.info("Cloned voice created: id={}, name='{}', baseVoice={}", saved.getId(), saved.getName(), saved.getBaseVoice());
        return ClonedVoiceResponse.from(saved);
    }

    @DeleteMapping("/cloned/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        ClonedVoice voice = voiceRepository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("ClonedVoice", id));
        voiceRepository.delete(voice);
    }
}
