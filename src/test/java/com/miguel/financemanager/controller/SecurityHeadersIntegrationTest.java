package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityHeadersIntegrationTest {

    private static final String HSTS = "Strict-Transport-Security";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;

    @BeforeEach
    void registerUser() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "headers@teste.com", "password", "password123"));

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    void authenticatedResponse_hasSecurityHeaders() throws Exception {
        expectSecurityHeaders(mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()));
    }

    @Test
    void unauthorizedResponse_hasSecurityHeaders() throws Exception {
        expectSecurityHeaders(mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized()));
    }

    // Em produção o TLS termina no proxy do Railway, que passa X-Forwarded-Proto.
    // Hoje esse cabeçalho é ignorado de propósito (forward-headers-strategy=none,
    // ver application.properties), por isso não há HSTS atrás do proxy. Quando
    // o item do backlog "HSTS atrás do proxy" for feito, este teste volta a
    // exigir o HSTS, e o ForwardedForRateLimitIntegrationTest tem de continuar verde.
    @Test
    void httpsBehindProxy_noHstsUntilProxyFilter() throws Exception {
        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HSTS));
    }

    @Test
    void plainHttp_doesNotSendHsts() throws Exception {
        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HSTS));
    }

    private void expectSecurityHeaders(ResultActions result) throws Exception {
        result
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }
}
