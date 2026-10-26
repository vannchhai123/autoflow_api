package com.autoclip.common.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class HealthController {

    private static final Pattern TIKTOK_VERIFY_PATTERN = Pattern.compile("tiktok([A-Za-z0-9_-]+)\\.txt$");

    @GetMapping({"/", "/health", "/api/health"})
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "autoclip-api"
        ));
    }

    @GetMapping(value = {
            "/{filename:tiktok.*}",
            "/api/accounts/oauth2/callback/tiktok/{filename:tiktok.*}"
    }, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> tiktokVerification(@PathVariable String filename) {
        Matcher m = TIKTOK_VERIFY_PATTERN.matcher(filename);
        if (m.find()) {
            return ResponseEntity.ok("tiktok-developers-site-verification=" + m.group(1));
        }
        return ResponseEntity.ok("tiktok-developers-site-verification=AB14Sz4wisXlu13NGnuLHkK1kiYfOMrw");
    }
}
