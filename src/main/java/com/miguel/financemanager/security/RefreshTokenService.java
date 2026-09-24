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
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    // Token acabado de emitir: o valor em bruto (vai para o cliente) e o id
    // da linha (vai para a claim "sid" do access token do mesmo par).
    public record IssuedToken(long id, String rawToken) {
    }

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-token-expiration-hours-short}")
    private long shortExpirationHours;

    @Value("${jwt.refresh-token-expiration-days-remembered}")
    private long rememberedExpirationDays;

    // Gera um refresh token novo (string aleatória de 64 bytes), grava só o
    // hash na BD, e devolve o valor em bruto, que só existe neste momento
    // e vai para o cliente uma única vez. rememberMe decide a validade e fica
    // gravado, para a renovação emitir o sucessor no mesmo modo.
    public IssuedToken createRefreshToken(User user, boolean rememberMe) {
        String rawToken = generateRawToken();

        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plus(lifetime(rememberMe)))
                .rememberMe(rememberMe)
                .revoked(false)
                .build();

        refreshTokenRepository.save(entity);
        return new IssuedToken(entity.getId(), rawToken);
    }

    // Valida o refresh token recebido e, se for válido, revoga-o (rotação:
    // cada refresh token só pode ser trocado uma vez por um novo par de tokens).
    // Devolve o token consumido: quem renova precisa do utilizador e do modo.
    public RefreshToken consumeRefreshToken(String rawToken) {
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

        return stored;
    }

    public void revokeToken(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    // Validade de um token novo: curta por omissão, longa com "Manter sessão iniciada".
    Duration lifetime(boolean rememberMe) {
        return rememberMe
                ? Duration.ofDays(rememberedExpirationDays)
                : Duration.ofHours(shortExpirationHours);
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
