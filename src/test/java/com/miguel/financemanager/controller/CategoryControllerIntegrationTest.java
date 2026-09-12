package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;

    @BeforeEach
    void registerUser() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "cattest@teste.com", "password", "password123"));

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    void listCategories_returnsSevenSeededIncludingProtectedOne() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode categories = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(categories).hasSize(7);

        boolean protectedFound = false;
        for (JsonNode node : categories) {
            if (node.get("name").asText().equals("Sem Categoria")) {
                protectedFound = node.get("defaultCategory").asBoolean();
            }
        }
        assertThat(protectedFound).isTrue();
    }

    @Test
    void createRenameAndDeleteCategory_fullLifecycle() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of("name", "Investimentos"));

        MvcResult createResult = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Investimentos"))
                .andReturn();

        long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        String renameBody = objectMapper.writeValueAsString(Map.of("name", "Poupança"));

        mockMvc.perform(put("/api/categories/" + id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(renameBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Poupança"));

        mockMvc.perform(delete("/api/categories/" + id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.length()").value(7));
    }

    @Test
    void createCategory_rejectsDuplicateName() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "Duplicada"));

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteProtectedCategory_isRejected() throws Exception {
        MvcResult listResult = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();

        JsonNode categories = objectMapper.readTree(listResult.getResponse().getContentAsString());

        long protectedId = -1;
        for (JsonNode node : categories) {
            if (node.get("defaultCategory").asBoolean()) {
                protectedId = node.get("id").asLong();
            }
        }

        mockMvc.perform(delete("/api/categories/" + protectedId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Não é possível apagar a categoria \"Sem Categoria\""));
    }
}
