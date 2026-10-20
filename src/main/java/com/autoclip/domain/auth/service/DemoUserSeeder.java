package com.autoclip.domain.auth.service;

import com.autoclip.domain.auth.entity.Role;
import com.autoclip.domain.auth.entity.User;
import com.autoclip.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoUserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${autoclip.admin.email:}")
    private String configuredAdminEmail;

    @Value("${autoclip.admin.password:}")
    private String configuredAdminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        String targetEmail = (configuredAdminEmail != null && !configuredAdminEmail.isBlank())
                ? configuredAdminEmail.trim()
                : "admin@autoclip.app";

        User targetUser = userRepository.findByEmail(targetEmail).orElseGet(() -> {
            String initialPassword = (configuredAdminPassword != null && !configuredAdminPassword.isBlank())
                    ? configuredAdminPassword
                    : "AutoClipAdmin123!";
            User newUser = new User();
            newUser.setEmail(targetEmail);
            newUser.setPasswordHash(passwordEncoder.encode(initialPassword));
            newUser.setDisplayName(targetEmail.contains("@") ? targetEmail.substring(0, targetEmail.indexOf("@")) : "Admin");
            newUser.setRole(Role.ADMIN);
            User saved = userRepository.save(newUser);
            log.info("Admin user successfully initialized: {}", targetEmail);
            return saved;
        });

        if (configuredAdminPassword != null && !configuredAdminPassword.isBlank()) {
            targetUser.setPasswordHash(passwordEncoder.encode(configuredAdminPassword));
            targetUser.setRole(Role.ADMIN);
            targetUser = userRepository.save(targetUser);
            log.info("Admin password synchronized with environment configuration for: {}", targetEmail);
        }

        Optional<User> demoUserOpt = userRepository.findByEmail("demo@autoclip.app");
        if (demoUserOpt.isPresent()) {
            User demoUser = demoUserOpt.get();
            log.info("Migrating data from demo user {} to {}", demoUser.getId(), targetUser.getId());
            try {
                jdbcTemplate.update("UPDATE social_accounts SET owner_id = ? WHERE owner_id = ?", targetUser.getId(), demoUser.getId());
                jdbcTemplate.update("UPDATE video_projects SET owner_id = ? WHERE owner_id = ?", targetUser.getId(), demoUser.getId());
                jdbcTemplate.update("UPDATE assets SET owner_id = ? WHERE owner_id = ?", targetUser.getId(), demoUser.getId());
                jdbcTemplate.update("UPDATE cloned_voices SET owner_id = ? WHERE owner_id = ?", targetUser.getId(), demoUser.getId());
            } catch (Exception e) {
                log.warn("Non-critical table update during demo user migration: {}", e.getMessage());
            }
            userRepository.delete(demoUser);
            log.info("Demo user removed successfully.");
        }
        userRepository.findByEmail("alice@example.com").ifPresent(userRepository::delete);
    }
}
