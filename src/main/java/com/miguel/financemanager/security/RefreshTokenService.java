package com.miguel.financemanager.security;

import com.miguel.financemanager.entity.RefreshToken;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-token-expiration-days}")
    private long refreshTokenExpirationDays;

    // Gera um refresh token novo (string aleatória de 64 bytes), grava só o
    // hash na BD, e devolve o valor em bruto, que só existe neste momento
    // e vai para o cliente uma única vez.
    public String createRefreshToken(User user) {
        String rawToken = generateRawToken();

        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plus(refreshTokenExpirationDays, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        refreshTokenRepository.save(entity);
        return rawToken;
    }

    // Valida o refresh token recebido e, se for válido, revoga-o (rotação:
    // cada refresh token só pode ser trocado uma vez por um novo par de tokens).
    public User consumeRefreshToken(String rawToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Refresh token inválido"));

        if (stored.isRevoked()) {
            throw new IllegalArgumentException("Refresh token já foi utilizado");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token expirado");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return stored.getUser();
    }

    public void revokeToken(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
