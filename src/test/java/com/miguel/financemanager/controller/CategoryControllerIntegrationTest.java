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
                Map.of("email", "cattest@teste.com", "password", "senha-de-teste-42"));

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

    // ---------- Cor ----------

    private MvcResult createCategory(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void seededCategoriesHaveNoColour() throws Exception {
        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].color", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void validColourIsStoredUppercaseAndReturnedEverywhere() throws Exception {
        MvcResult created = createCategory(accessToken, Map.of("name", "Viagens", "color", "#1f5e6b"));
        org.assertj.core.api.Assertions.assertThat(created.getResponse().getStatus()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(
                objectMapper.readTree(created.getResponse().getContentAsString()).get("color").asText()).isEqualTo("#1F5E6B");

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$[?(@.name == 'Viagens')].color").value("#1F5E6B"));

        mockMvc.perform(put("/api/categories/" + idOf(created))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Viagens", "color", "#AbCdEf"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#ABCDEF"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"red", "#12", "#GGGGGG", "url(x)", "#ff0000;x", ""})
    void invalidColourIsRejectedWith400AndShortMessage(String colour) throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Teste cor", "color", colour))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cor inválida. Usa #RRGGBB."));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$[?(@.name == 'Teste cor')]").isEmpty());
    }

    @Test
    void colourCanBeClearedWithNullOrByOmittingIt() throws Exception {
        long id = idOf(createCategory(accessToken, Map.of("name", "Casa 2", "color", "#1F5E6B")));
        Map<String, Object> withNull = new java.util.HashMap<>();
        withNull.put("name", "Casa 2");
        withNull.put("color", null);

        mockMvc.perform(put("/api/categories/" + id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withNull)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value(org.hamcrest.Matchers.nullValue()));

        long other = idOf(createCategory(accessToken, Map.of("name", "Casa 3", "color", "#1F5E6B")));
        mockMvc.perform(put("/api/categories/" + other)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Casa 3"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void protectedCategoryColourCannotChange() throws Exception {
        MvcResult list = mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + accessToken)).andReturn();
        long protectedId = 0;
        for (com.fasterxml.jackson.databind.JsonNode c : objectMapper.readTree(list.getResponse().getContentAsString())) {
            if (c.get("defaultCategory").asBoolean()) protectedId = c.get("id").asLong();
        }

        mockMvc.perform(put("/api/categories/" + protectedId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Sem Categoria", "color", "#1F5E6B"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Sem Categoria")));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$[?(@.defaultCategory == true)].color").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void anotherUsersCategoryCannotBeRecoloured() throws Exception {
        long mine = idOf(createCategory(accessToken, Map.of("name", "Minha", "color", "#1F5E6B")));

        MvcResult other = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "outro-cor@teste.com", "password", "senha-de-teste-42"))))
                .andReturn();
        String otherToken = objectMapper.readTree(other.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(put("/api/categories/" + mine)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Minha", "color", "#000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Categoria não encontrada"));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$[?(@.name == 'Minha')].color").value("#1F5E6B"));
    }

    @Test
    void newCategoryIsListedBeforeTheProtectedOne() throws Exception {
        createCategory(accessToken, Map.of("name", "Nova no fim"));

        MvcResult list = mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        com.fasterxml.jackson.databind.JsonNode categories = objectMapper.readTree(list.getResponse().getContentAsString());
        int last = categories.size() - 1;

        org.assertj.core.api.Assertions.assertThat(categories.get(last).get("defaultCategory").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(categories.get(last - 1).get("name").asText()).isEqualTo("Nova no fim");
        for (int i = 0; i < last; i++) {
            org.assertj.core.api.Assertions.assertThat(categories.get(i).get("defaultCategory").asBoolean()).isFalse();
        }
    }
}
