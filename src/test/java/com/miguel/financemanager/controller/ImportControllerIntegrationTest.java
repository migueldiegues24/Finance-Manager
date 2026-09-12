package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImportControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;

    @BeforeEach
    void registerUser() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "importtest@teste.com", "password", "password123"));

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private MockMultipartFile csvFile() {
        String csv = "date,description,amount\n"
                + "2026-09-01,Uber viagem centro,-8.50\n"
                + "2026-09-03,Renda casa,-500.00\n";
        return new MockMultipartFile("file", "extrato.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parse_returnsTransactionsWithoutPersisting() throws Exception {
        mockMvc.perform(multipart("/api/imports/parse")
                        .file(csvFile())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("extrato.csv"))
                .andExpect(jsonPath("$.transactions.length()").value(2))
                .andExpect(jsonPath("$.transactions[0].duplicate").value(false));
    }

    @Test
    void confirm_persistsTransactionsAndReturnsSummary() throws Exception {
        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "filename", "extrato.csv",
                "transactions", List.of(
                        Map.of("date", "2026-09-01", "description", "Renda casa", "amount", -500.00)
                )
        ));

        mockMvc.perform(post("/api/imports/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsSaved").value(1))
                .andExpect(jsonPath("$.filename").value("extrato.csv"));
    }

    @Test
    void parse_marksAlreadyImportedTransactionAsDuplicate() throws Exception {
        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "filename", "extrato.csv",
                "transactions", List.of(
                        Map.of("date", "2026-09-01", "description", "Uber viagem centro", "amount", -8.50)
                )
        ));

        mockMvc.perform(post("/api/imports/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/imports/parse")
                        .file(csvFile())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].duplicate").value(true));
    }
}
