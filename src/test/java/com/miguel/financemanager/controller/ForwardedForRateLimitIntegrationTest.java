package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Como no Railway: todos os pedidos chegam do mesmo proxy (mesmo remoteAddr)
// e o IP do visitante vem no fim do X-Forwarded-For.
@SpringBootTest(properties = {
        "auth.trust-forwarded-for=true",
        "spring.datasource.url=jdbc:h2:mem:forwardedfor;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ForwardedForRateLimitIntegrationTest {

    private static final String PROXY = "10.0.0.2";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ResultActions failLogin(String email, String forwardedFor) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(PROXY);
                    return request;
                })
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "password-errada"))));
    }

    @Test
    void visitorsBehindSameProxy_areLimitedSeparately() throws Exception {
        for (int i = 0; i < 20; i++) {
            failLogin("a" + i + "@teste.com", "198.51.100.20").andExpect(status().isUnauthorized());
        }
        failLogin("mais@teste.com", "198.51.100.20").andExpect(status().isTooManyRequests());

        failLogin("outro@teste.com", "203.0.113.80").andExpect(status().isUnauthorized());
    }

    // O cliente pode pôr o que quiser no início do cabeçalho; só conta o
    // último valor, o que o proxy acrescentou.
    @Test
    void spoofedLeadingValues_doNotEscapeTheLimit() throws Exception {
        for (int i = 0; i < 20; i++) {
            failLogin("b" + i + "@teste.com", "1.1.1." + i + ", 198.51.100.21").andExpect(status().isUnauthorized());
        }

        failLogin("mais@teste.com", "9.9.9.9, 198.51.100.21").andExpect(status().isTooManyRequests());
    }
}
