package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miguel.financemanager.entity.RefreshToken;
import com.miguel.financemanager.repository.RefreshTokenRepository;
import com.miguel.financemanager.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// PUT /api/account/password: exige a password atual, aplica a PasswordPolicy,
// revoga todas as sessões da conta e devolve um par novo (no mesmo modo) ao
// dispositivo que fez o pedido.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountPasswordIntegrationTest {

    private static final String EMAIL = "conta@teste.com";
    private static final String PASSWORD = "senha-de-teste-42";
    private static final String NEW_PASSWORD = "outra-senha-muito-boa-7";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void registerUser() throws Exception {
        send(post("/api/auth/register"), null, Map.of("email", EMAIL, "password", PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_withoutToken_returns401() throws Exception {
        send(put("/api/account/password"), null, body(PASSWORD, NEW_PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_withWrongCurrentPassword_returns400AndChangesNothing() throws Exception {
        JsonNode session = login(EMAIL, PASSWORD, false);

        send(put("/api/account/password"), access(session), body("password-errada-123", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A password atual está incorreta."));

        login(EMAIL, PASSWORD, false);
        assertThat(stored(session).isRevoked()).isFalse();
    }

    @Test
    void changePassword_appliesPasswordPolicy() throws Exception {
        JsonNode session = login(EMAIL, PASSWORD, false);

        send(put("/api/account/password"), access(session), body(PASSWORD, "password1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Esta password é demasiado comum ou fácil de adivinhar. Escolhe outra."));
        send(put("/api/account/password"), access(session), body(PASSWORD, "curta"))
                .andExpect(status().isBadRequest());
        send(put("/api/account/password"), access(session), body(PASSWORD, "conta-e-a-minha-senha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A password não pode conter o teu email."));
    }

    @Test
    void changePassword_rejectsSamePassword() throws Exception {
        JsonNode session = login(EMAIL, PASSWORD, false);

        send(put("/api/account/password"), access(session), body(PASSWORD, PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A nova password tem de ser diferente da atual."));
    }

    @Test
    void changePassword_revokesEverySessionAndIssuesNewPairForThisDevice() throws Exception {
        JsonNode laptop = login(EMAIL, PASSWORD, false);
        JsonNode phone = login(EMAIL, PASSWORD, true);

        JsonNode renewed = json(send(put("/api/account/password"), access(laptop), body(PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk()));

        // As sessões antigas (incluindo a deste dispositivo) já não renovam.
        refresh(laptop).andExpect(status().isBadRequest());
        refresh(phone).andExpect(status().isBadRequest());
        // O par novo funciona e a claim "sid" aponta para ele.
        assertThat(jwtService.extractSessionId(renewed.get("accessToken").asText()))
                .isEqualTo(stored(renewed).getId());
        refresh(renewed).andExpect(status().isOk());

        // Só a password nova entra.
        send(post("/api/auth/login"), null, Map.of("email", EMAIL, "password", PASSWORD))
                .andExpect(status().isUnauthorized());
        login(EMAIL, NEW_PASSWORD, false);
    }

    @Test
    void changePassword_keepsModeOfCurrentSession() throws Exception {
        JsonNode remembered = login(EMAIL, PASSWORD, true);
        JsonNode renewed = json(send(put("/api/account/password"), access(remembered), body(PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk()));
        assertThat(stored(renewed).isRememberMe()).isTrue();

        JsonNode shortSession = login(EMAIL, NEW_PASSWORD, false);
        JsonNode renewedAgain = json(send(put("/api/account/password"), access(shortSession),
                body(NEW_PASSWORD, PASSWORD + "-x"))
                .andExpect(status().isOk()));
        assertThat(stored(renewedAgain).isRememberMe()).isFalse();
    }

    @Test
    void changePassword_doesNotTouchOtherUsersSessions() throws Exception {
        send(post("/api/auth/register"), null, Map.of("email", "outra@teste.com", "password", PASSWORD))
                .andExpect(status().isOk());
        JsonNode other = login("outra@teste.com", PASSWORD, false);
        JsonNode mine = login(EMAIL, PASSWORD, false);

        send(put("/api/account/password"), access(mine), body(PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk());

        assertThat(stored(other).isRevoked()).isFalse();
        refresh(other).andExpect(status().isOk());
        login("outra@teste.com", PASSWORD, false);
    }

    @Test
    void changePassword_blocksAfterFiveWrongPasswords_evenWithTheRightOne() throws Exception {
        JsonNode session = login(EMAIL, PASSWORD, false);

        for (int i = 0; i < 5; i++) {
            send(put("/api/account/password"), access(session), body("password-errada-123", NEW_PASSWORD))
                    .andExpect(status().isBadRequest());
        }

        send(put("/api/account/password"), access(session), body(PASSWORD, NEW_PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("30"));
        login(EMAIL, PASSWORD, false);
    }

    private static Map<String, String> body(String current, String next) {
        return Map.of("currentPassword", current, "newPassword", next);
    }

    private JsonNode login(String email, String password, boolean rememberMe) throws Exception {
        return json(send(post("/api/auth/login"), null,
                Map.of("email", email, "password", password, "rememberMe", rememberMe))
                .andExpect(status().isOk()));
    }

    private ResultActions refresh(JsonNode pair) throws Exception {
        return send(post("/api/auth/refresh"), null, Map.of("refreshToken", pair.get("refreshToken").asText()));
    }

    private RefreshToken stored(JsonNode pair) {
        return refreshTokenRepository.findById(jwtService.extractSessionId(access(pair))).orElseThrow();
    }

    private static String access(JsonNode pair) {
        return pair.get("accessToken").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request,
                               String accessToken, Map<String, ?> body) throws Exception {
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return mockMvc.perform(request
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
