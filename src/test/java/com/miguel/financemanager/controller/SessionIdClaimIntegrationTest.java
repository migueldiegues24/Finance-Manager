package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miguel.financemanager.repository.RefreshTokenRepository;
import com.miguel.financemanager.security.JwtService;
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
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A claim "sid" do access token é o id do refresh token emitido no mesmo par,
// e acompanha a rotação.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessionIdClaimIntegrationTest {

    private static final String PASSWORD = "senha-de-teste-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void register_accessTokenCarriesIdOfItsRefreshToken() throws Exception {
        JsonNode pair = send("/api/auth/register", Map.of("email", "sid@teste.com", "password", PASSWORD));

        assertSessionMatches(pair);
    }

    @Test
    void refresh_accessTokenCarriesIdOfTheNewRefreshToken() throws Exception {
        JsonNode first = send("/api/auth/register", Map.of("email", "sid2@teste.com", "password", PASSWORD));
        JsonNode renewed = send("/api/auth/refresh", Map.of("refreshToken", first.get("refreshToken").asText()));

        assertSessionMatches(renewed);
        assertThat(sid(renewed)).isNotEqualTo(sid(first));
    }

    private void assertSessionMatches(JsonNode pair) throws Exception {
        long storedId = refreshTokenRepository.findByTokenHash(hash(pair.get("refreshToken").asText()))
                .orElseThrow().getId();
        assertThat(sid(pair)).isEqualTo(storedId);
    }

    private Long sid(JsonNode pair) {
        return jwtService.extractSessionId(pair.get("accessToken").asText());
    }

    private JsonNode send(String path, Map<String, String> body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(
                        post(path)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static String hash(String rawToken) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
