package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategorizationRuleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;
    private long transporteCategoryId;

    @BeforeEach
    void registerUserAndFindCategory() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "ruletest@teste.com", "password", "password123"));

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        MvcResult categoriesResult = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();

        JsonNode categories = objectMapper.readTree(categoriesResult.getResponse().getContentAsString());
        transporteCategoryId = -1;
        for (JsonNode node : categories) {
            if (node.get("name").asText().equals("Transporte")) {
                transporteCategoryId = node.get("id").asLong();
            }
        }
    }

    @Test
    void listRules_startsEmpty() throws Exception {
        mockMvc.perform(get("/api/rules").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createRule_appendsWithIncrementingPriorityWhenOmitted() throws Exception {
        String firstBody = objectMapper.writeValueAsString(
                Map.of("keyword", "uber", "categoryId", transporteCategoryId));

        mockMvc.perform(post("/api/rules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(0));

        String secondBody = objectMapper.writeValueAsString(
                Map.of("keyword", "bolt", "categoryId", transporteCategoryId));

        mockMvc.perform(post("/api/rules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(1));

        mockMvc.perform(get("/api/rules").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void createRule_rejectsUnknownCategory() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("keyword", "uber", "categoryId", 999999L));

        mockMvc.perform(post("/api/rules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRule_removesItFromList() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("keyword", "uber", "categoryId", transporteCategoryId));

        MvcResult createResult = mockMvc.perform(post("/api/rules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        long ruleId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(delete("/api/rules/" + ruleId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rules").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
