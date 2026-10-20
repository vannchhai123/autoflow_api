package com.autoclip.domain.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    private static final String DEFAULT_SECRET = "ZGV2LW9ubHktc2VjcmV0LWtleS1jaGFuZ2UtbWUtaW4tcHJvZHVjdGlvbi0xMjM0NTY3ODkw";

    public JwtService(@Value("${autoclip.security.jwt.secret:}") String secret,
                      @Value("${autoclip.security.jwt.expiration-minutes:60}") long expirationMinutes) {
        String activeSecret = (secret != null && !secret.isBlank()) ? secret : DEFAULT_SECRET;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(activeSecret));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String generateToken(UserDetails user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public long expiresInSeconds() {
        return expiration.toSeconds();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public boolean isValid(String token, UserDetails user) {
        try {
            Claims claims = parse(token);
            return claims.getSubject().equals(user.getUsername())
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
