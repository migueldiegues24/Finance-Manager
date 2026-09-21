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
                .andExpect(jsonPath("$.overallTotal").value(0))
                .andExpect(jsonPath("$.totalIncome").value(0))
                .andExpect(jsonPath("$.totalExpenses").value(0))
                .andExpect(jsonPath("$.net").value(0));
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
                .andExpect(jsonPath("$.overallTotal").value(508.50))
                .andExpect(jsonPath("$.totalIncome").value(1200.00))
                .andExpect(jsonPath("$.totalExpenses").value(508.50))
                .andExpect(jsonPath("$.net").value(691.50));
    }

    private String registerAndLogin(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void confirm(String token, List<Map<String, Object>> transactions) throws Exception {
        mockMvc.perform(post("/api/imports/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "filename", "extrato.csv", "transactions", transactions))))
                .andExpect(status().isOk());
    }

    @Test
    void summary_netIsNegativeWhenExpensesExceedIncomeAndIgnoresOtherMonths() throws Exception {
        confirm(accessToken, List.of(
                Map.of("date", "2026-09-01", "description", "Renda", "amount", -500.00),
                Map.of("date", "2026-09-15", "description", "Reembolso", "amount", 20.00),
                Map.of("date", "2026-08-31", "description", "Salario agosto", "amount", 1200.00),
                Map.of("date", "2026-10-01", "description", "Renda outubro", "amount", -500.00)
        ));

        mockMvc.perform(get("/api/dashboard/summary?month=2026-09")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(20.00))
                .andExpect(jsonPath("$.totalExpenses").value(500.00))
                .andExpect(jsonPath("$.net").value(-480.00))
                .andExpect(jsonPath("$.overallTotal").value(500.00));
    }

    @Test
    void months_returnsEmptyListWhenNoTransactions() throws Exception {
        mockMvc.perform(get("/api/dashboard/months")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void months_returnsDistinctMonthsAscendingOnlyForCurrentUser() throws Exception {
        confirm(accessToken, List.of(
                Map.of("date", "2026-09-01", "description", "Uber", "amount", -8.50),
                Map.of("date", "2026-09-20", "description", "Renda", "amount", -500.00),
                Map.of("date", "2025-12-24", "description", "Presente", "amount", -30.00),
                Map.of("date", "2026-02-10", "description", "Salario", "amount", 1200.00)
        ));

        String otherToken = registerAndLogin("outro@teste.com");
        confirm(otherToken, List.of(Map.of("date", "2024-05-01", "description", "Outro", "amount", -1.00)));

        mockMvc.perform(get("/api/dashboard/months")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]").value("2025-12"))
                .andExpect(jsonPath("$[1]").value("2026-02"))
                .andExpect(jsonPath("$[2]").value("2026-09"));
    }

    @Test
    void months_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboard/months"))
                .andExpect(status().is4xxClientError());
    }
}
