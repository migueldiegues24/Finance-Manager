package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miguel.financemanager.entity.RefreshToken;
import com.miguel.financemanager.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Modo da sessão ("Manter sessão iniciada" ou não) gravado em cada refresh
// token, e herdado na renovação. Validades de application-test.properties:
// 12 h (curta) e 14 dias (recordada).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RefreshTokenModeIntegrationTest {

    private static final String EMAIL = "modo@teste.com";
    private static final String PASSWORD = "senha-de-teste-42";
    private static final Duration SHORT = Duration.ofHours(12);
    private static final Duration REMEMBERED = Duration.ofDays(14);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void registerUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void register_issuesShortSession() throws Exception {
        String registerBody = objectMapper.writeValueAsString(Map.of("email", "novo@teste.com", "password", PASSWORD));
        String registered = tokenFrom(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertMode(registered, false, SHORT);
    }

    @Test
    void login_withoutRememberMe_storesShortMode() throws Exception {
        assertMode(login(null), false, SHORT);
    }

    @Test
    void login_withRememberMeFalse_storesShortMode() throws Exception {
        assertMode(login(false), false, SHORT);
    }

    @Test
    void login_withRememberMe_storesRememberedMode() throws Exception {
        assertMode(login(true), true, REMEMBERED);
    }

    @Test
    void refresh_keepsShortSessionShort() throws Exception {
        String renewed = refresh(login(false));
        assertMode(renewed, false, SHORT);
        // E continua curto na renovação seguinte.
        assertMode(refresh(renewed), false, SHORT);
    }

    @Test
    void refresh_keepsRememberedSessionAtFourteenDays() throws Exception {
        String renewed = refresh(login(true));
        assertMode(renewed, true, REMEMBERED);
        assertMode(refresh(renewed), true, REMEMBERED);
    }

    @Test
    void refresh_withExpiredToken_returns400() throws Exception {
        String token = login(false);
        RefreshToken stored = find(token);
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.saveAndFlush(stored);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", token))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Refresh token expirado"));
    }

    // rememberMe null = campo ausente do pedido.
    private String login(Boolean rememberMe) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("email", EMAIL, "password", PASSWORD));
        if (rememberMe != null) body.put("rememberMe", rememberMe);

        return tokenFrom(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String refresh(String refreshToken) throws Exception {
        return tokenFrom(mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String tokenFrom(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("refreshToken").asText();
    }

    private void assertMode(String rawToken, boolean rememberMe, Duration lifetime) throws Exception {
        RefreshToken stored = find(rawToken);
        assertThat(stored.isRememberMe()).isEqualTo(rememberMe);
        // Validade contada a partir da emissão, com folga para a duração do teste.
        Instant expected = Instant.now().plus(lifetime);
        assertThat(stored.getExpiresAt()).isBetween(expected.minusSeconds(60), expected);
    }

    private RefreshToken find(String rawToken) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        String hash = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        return refreshTokenRepository.findByTokenHash(hash).orElseThrow();
    }
}
