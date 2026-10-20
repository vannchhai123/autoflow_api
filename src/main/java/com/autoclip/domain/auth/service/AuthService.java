package com.autoclip.domain.auth.service;

import com.autoclip.common.exception.BusinessException;
import com.autoclip.domain.auth.dto.AuthDtos.AuthResponse;
import com.autoclip.domain.auth.dto.AuthDtos.LoginRequest;
import com.autoclip.domain.auth.dto.AuthDtos.RegisterRequest;
import com.autoclip.domain.auth.entity.User;
import com.autoclip.domain.auth.repository.UserRepository;
import com.autoclip.domain.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Email already registered", HttpStatus.CONFLICT);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password().trim()));
        user.setDisplayName(request.displayName().trim());
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim(), request.password().trim()));
        return toResponse((User) authentication.getPrincipal());
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer", jwtService.expiresInSeconds());
    }
}
