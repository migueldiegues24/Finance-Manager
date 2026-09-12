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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;

    @BeforeEach
    void registerUser() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "dashboardtest@teste.com", "password", "password123"));

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    void summary_returnsZeroTotalsWhenNoTransactions() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary?month=2026-09")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.totals.length()").value(0))
                .andExpect(jsonPath("$.overallTotal").value(0));
    }

    @Test
    void summary_reflectsConfirmedImportTotalsAndExcludesIncome() throws Exception {
        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "filename", "extrato.csv",
                "transactions", List.of(
                        Map.of("date", "2026-09-01", "description", "Uber viagem centro", "amount", -8.50),
                        Map.of("date", "2026-09-03", "description", "Renda casa", "amount", -500.00),
                        Map.of("date", "2026-09-03", "description", "Transferencia recebida", "amount", 1200.00)
                )
        ));

        mockMvc.perform(post("/api/imports/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk());

        // Sem regras criadas, as duas despesas caem em "Outros";
        // a receita (Transferencia) fica de fora da soma.
        mockMvc.perform(get("/api/dashboard/summary?month=2026-09")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallTotal").value(508.50));
    }
}
