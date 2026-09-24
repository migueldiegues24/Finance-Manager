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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /api/account/export: devolve tudo o que é do utilizador (perfil,
// categorias, regras, importações, transações), nada de outra conta, e
// nunca hashes de password, tokens nem a fingerprint das transações.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountExportIntegrationTest {

    private static final String PASSWORD = "senha-de-teste-42";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode pairA;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        pairA = register("export-a@teste.com");
        tokenA = pairA.get("accessToken").asText();
        tokenB = register("export-b@teste.com").get("accessToken").asText();

        long casaA = categoryId(tokenA, "Casa");
        send(post("/api/rules"), tokenA, Map.of("keyword", "supermercado", "categoryId", casaA))
                .andExpect(status().isOk());
        send(post("/api/categories"), tokenA, Map.of("name", "Viagens A", "color", "#1F77B4"))
                .andExpect(status().isOk());
        confirm(tokenA, "Supermercado A", -42.10);
        confirm(tokenA, "Ordenado A", 1500.00);

        send(post("/api/categories"), tokenB, Map.of("name", "Segredo B"))
                .andExpect(status().isOk());
        send(post("/api/rules"), tokenB, Map.of("keyword", "farmacia-b", "categoryId", categoryId(tokenB, "Saúde")))
                .andExpect(status().isOk());
        confirm(tokenB, "Farmacia B", -7.30);
    }

    @Test
    void export_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/account/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void export_containsEverythingOfTheUser() throws Exception {
        JsonNode export = json(send(get("/api/account/export"), tokenA, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"finance-manager-"))));

        assertThat(export.get("profile").get("email").asText()).isEqualTo("export-a@teste.com");
        assertThat(export.get("profile").has("createdAt")).isTrue();
        assertThat(export.has("exportedAt")).isTrue();

        // 7 categorias iniciais + "Viagens A".
        assertThat(texts(export.get("categories"), "name")).hasSize(8).contains("Casa", "Viagens A", "Sem Categoria");

        JsonNode rules = export.get("rules");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).get("keyword").asText()).isEqualTo("supermercado");
        assertThat(rules.get(0).get("categoryName").asText()).isEqualTo("Casa");

        assertThat(export.get("imports")).hasSize(2);
        JsonNode transactions = export.get("transactions");
        assertThat(texts(transactions, "description")).containsExactlyInAnyOrder("Supermercado A", "Ordenado A");
        JsonNode groceries = find(transactions, "description", "Supermercado A");
        assertThat(groceries.get("amount").decimalValue()).isEqualByComparingTo("-42.10");
        // A regra aplicou-se: a transação vem com a categoria e a importação da conta.
        assertThat(groceries.get("categoryName").asText()).isEqualTo("Casa");
        assertThat(ids(export.get("imports"))).contains(groceries.get("importId").asLong());
    }

    @Test
    void export_hasNothingFromOtherUser() throws Exception {
        String body = send(get("/api/account/export"), tokenA, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("export-b@teste.com", "Segredo B", "farmacia-b", "Farmacia B");
    }

    @Test
    void export_hasNoSecretsNorFingerprints() throws Exception {
        JsonNode export = json(send(get("/api/account/export"), tokenA, null).andExpect(status().isOk()));
        String body = export.toString();

        assertThat(body).doesNotContain(pairA.get("refreshToken").asText(), "$2a$", "$2b$");
        assertThat(fieldNames(export.get("profile"))).containsExactlyInAnyOrder("email", "createdAt");
        assertThat(fieldNames(export)).containsExactlyInAnyOrder(
                "exportedAt", "profile", "categories", "rules", "imports", "transactions");
        assertThat(fieldNames(export.get("transactions").get(0))).doesNotContain("hash");
    }

    @Test
    void export_isLimitedToTenPerHour() throws Exception {
        for (int i = 0; i < 10; i++) {
            send(get("/api/account/export"), tokenA, null).andExpect(status().isOk());
        }
        send(get("/api/account/export"), tokenA, null).andExpect(status().isTooManyRequests());
        // O limite é por conta.
        send(get("/api/account/export"), tokenB, null).andExpect(status().isOk());
    }

    private JsonNode register(String email) throws Exception {
        return json(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()));
    }

    private void confirm(String token, String description, double amount) throws Exception {
        send(post("/api/imports/confirm"), token, Map.of(
                "filename", "extrato.csv",
                "transactions", List.of(Map.of("date", "2026-09-05", "description", description, "amount", amount))))
                .andExpect(status().isOk());
    }

    private long categoryId(String token, String name) throws Exception {
        return find(json(send(get("/api/categories"), token, null)), "name", name).get("id").asLong();
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (node.get(field).asText().equals(value)) return node;
        }
        throw new AssertionError(field + "=" + value + " não encontrado");
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get(field).asText()));
        return values;
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

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
}
