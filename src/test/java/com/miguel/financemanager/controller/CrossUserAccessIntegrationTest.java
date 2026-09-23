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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Isolamento entre contas, pelo HTTP: o utilizador B não vê, não altera e não
// apaga nada do utilizador A. Ids no path de outra conta dão 404, iguais aos
// de um id que não existe (não se confirma a existência); um categoryId
// alheio no body dá 400 (erro de validação do pedido). Depois de cada
// tentativa confirma-se que os dados de A ficaram iguais.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CrossUserAccessIntegrationTest {

    private static final long MISSING_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenA;
    private String tokenB;
    private long transactionA;
    private long categoryA;
    private long ruleA;
    private long transactionB;
    private long categoryB;

    @BeforeEach
    void setUp() throws Exception {
        tokenA = register("cross-a@teste.com");
        tokenB = register("cross-b@teste.com");

        confirm(tokenA, "Supermercado A", -42.10);
        confirm(tokenB, "Farmacia B", -7.30);

        transactionA = transactions(tokenA).get(0).get("id").asLong();
        transactionB = transactions(tokenB).get(0).get("id").asLong();
        categoryA = categoryId(tokenA, "Casa");
        categoryB = categoryId(tokenB, "Saúde");
        ruleA = json(send(post("/api/rules"), tokenA, Map.of("keyword", "supermercado", "categoryId", categoryA))
                .andExpect(status().isOk())).get("id").asLong();
    }

    // --- Leitura ---

    @Test
    void transactionList_showsNothingFromOtherUser() throws Exception {
        JsonNode list = transactions(tokenB);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asLong()).isEqualTo(transactionB);
        assertThat(list.get(0).get("description").asText()).isEqualTo("Farmacia B");
    }

    @Test
    void categoryAndRuleLists_showNothingFromOtherUser() throws Exception {
        List<Long> categoryIds = ids(json(send(get("/api/categories"), tokenB, null).andExpect(status().isOk())));
        assertThat(categoryIds).doesNotContain(categoryA).contains(categoryB);

        send(get("/api/rules"), tokenB, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void dashboard_showsNothingFromOtherUser() throws Exception {
        send(get("/api/dashboard/summary?month=2026-09"), tokenB, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(7.30))
                .andExpect(jsonPath("$.overallTotal").value(7.30));
    }

    // --- Transações ---

    @Test
    void cannotRecategoriseOtherUsersTransaction_404() throws Exception {
        long categoryBefore = transactions(tokenA).get(0).get("categoryId").asLong();

        send(put("/api/transactions/" + transactionA + "/category"), tokenB, Map.of("categoryId", categoryB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Transação não encontrada"));

        assertThat(transactions(tokenA).get(0).get("categoryId").asLong()).isEqualTo(categoryBefore);
    }

    @Test
    void otherUsersTransaction_looksExactlyLikeMissingOne() throws Exception {
        String foreign = send(put("/api/transactions/" + transactionA + "/category"), tokenB,
                Map.of("categoryId", categoryB)).andReturn().getResponse().getContentAsString();
        String missing = send(put("/api/transactions/" + MISSING_ID + "/category"), tokenB,
                Map.of("categoryId", categoryB)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(foreign).isEqualTo(missing);
    }

    // A transação é de B (o alvo existe); o erro está no body, que aponta
    // para uma categoria de A: 400 de validação, não 404.
    @Test
    void cannotApplyOtherUsersCategoryToOwnTransaction_400() throws Exception {
        long categoryBefore = transactions(tokenB).get(0).get("categoryId").asLong();

        send(put("/api/transactions/" + transactionB + "/category"), tokenB, Map.of("categoryId", categoryA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Categoria não encontrada"));

        assertThat(transactions(tokenB).get(0).get("categoryId").asLong()).isEqualTo(categoryBefore);
    }

    // --- Categorias ---

    @Test
    void cannotEditOtherUsersCategory_404() throws Exception {
        send(put("/api/categories/" + categoryA), tokenB, Map.of("name", "Roubada", "color", "#000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Categoria não encontrada"));

        assertThat(categoryNames(tokenA)).contains("Casa").doesNotContain("Roubada");
    }

    @Test
    void cannotDeleteOtherUsersCategory_404_andItsDataStays() throws Exception {
        long transactionCategoryBefore = transactions(tokenA).get(0).get("categoryId").asLong();

        send(delete("/api/categories/" + categoryA), tokenB, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Categoria não encontrada"));

        assertThat(ids(json(send(get("/api/categories"), tokenA, null)))).contains(categoryA);
        assertThat(ids(json(send(get("/api/rules"), tokenA, null)))).contains(ruleA);
        assertThat(transactions(tokenA).get(0).get("categoryId").asLong()).isEqualTo(transactionCategoryBefore);
    }

    @Test
    void otherUsersCategory_looksExactlyLikeMissingOne() throws Exception {
        String foreign = send(delete("/api/categories/" + categoryA), tokenB, null)
                .andReturn().getResponse().getContentAsString();
        String missing = send(delete("/api/categories/" + MISSING_ID), tokenB, null)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(foreign).isEqualTo(missing);
    }

    // --- Regras ---

    @Test
    void cannotDeleteOtherUsersRule_404() throws Exception {
        send(delete("/api/rules/" + ruleA), tokenB, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Regra não encontrada"));

        assertThat(ids(json(send(get("/api/rules"), tokenA, null)))).contains(ruleA);
    }

    @Test
    void cannotCreateRuleWithOtherUsersCategory_400() throws Exception {
        send(post("/api/rules"), tokenB, Map.of("keyword", "farmacia", "categoryId", categoryA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Categoria não encontrada"));

        send(get("/api/rules"), tokenB, null).andExpect(jsonPath("$.length()").value(0));
    }

    // --- Auxiliares ---

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void confirm(String token, String description, double amount) throws Exception {
        send(post("/api/imports/confirm"), token, Map.of(
                "filename", "extrato.csv",
                "transactions", List.of(Map.of("date", "2026-09-05", "description", description, "amount", amount))))
                .andExpect(status().isOk());
    }

    private JsonNode transactions(String token) throws Exception {
        return json(send(get("/api/transactions?month=2026-09"), token, null).andExpect(status().isOk()));
    }

    private long categoryId(String token, String name) throws Exception {
        for (JsonNode category : json(send(get("/api/categories"), token, null))) {
            if (category.get("name").asText().equals(name)) {
                return category.get("id").asLong();
            }
        }
        throw new AssertionError("Categoria " + name + " não encontrada");
    }

    private List<String> categoryNames(String token) throws Exception {
        List<String> names = new ArrayList<>();
        json(send(get("/api/categories"), token, null)).forEach(c -> names.add(c.get("name").asText()));
        return names;
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }
}
