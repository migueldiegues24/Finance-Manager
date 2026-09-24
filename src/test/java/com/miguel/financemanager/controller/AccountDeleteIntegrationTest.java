package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DELETE /api/account: com a password certa apaga a conta e tudo o que é
// dela, sem deixar linhas para trás e sem tocar noutras contas. Em H2 as FK
// do esquema gerado não têm cascata, por isso isto também prova a ordem.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountDeleteIntegrationTest {

    private static final String EMAIL = "apagar@teste.com";
    private static final String OTHER_EMAIL = "fica@teste.com";
    private static final String PASSWORD = "senha-de-teste-42";

    private static final List<String> OWNED = List.of(
            "Transaction", "CategorizationRule", "StatementImport", "Category", "RefreshToken");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode pair;
    private String otherToken;

    @BeforeEach
    void setUp() throws Exception {
        pair = register(EMAIL);
        otherToken = register(OTHER_EMAIL).get("accessToken").asText();
        seed(access(pair), "A");
        seed(otherToken, "B");
        // Mais uma sessão (recordada) além da do registo.
        login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void delete_withoutToken_returns401() throws Exception {
        send(delete("/api/account"), null, Map.of("password", PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void delete_withoutPassword_returns400() throws Exception {
        send(delete("/api/account"), access(pair), Map.of()).andExpect(status().isBadRequest());
        assertThat(userExists(EMAIL)).isTrue();
    }

    @Test
    void delete_withWrongPassword_returns400AndKeepsEverything() throws Exception {
        Map<String, Long> before = counts(EMAIL);

        send(delete("/api/account"), access(pair), Map.of("password", "password-errada-123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A password atual está incorreta."));

        assertThat(userExists(EMAIL)).isTrue();
        assertThat(counts(EMAIL)).isEqualTo(before);
    }

    @Test
    void delete_removesAccountAndEverythingItOwns_leavingOtherUsersIntact() throws Exception {
        long userId = entityManager.createQuery("SELECT u.id FROM User u WHERE u.email = :email", Long.class)
                .setParameter("email", EMAIL)
                .getSingleResult();
        Map<String, Long> before = countsByUserId(userId);
        Map<String, Long> otherBefore = counts(OTHER_EMAIL);
        // Garante que o teste apaga mesmo alguma coisa de cada tabela.
        assertThat(before.values()).allMatch(count -> count > 0);

        send(delete("/api/account"), access(pair), Map.of("password", PASSWORD))
                .andExpect(status().isNoContent());

        assertThat(userExists(EMAIL)).isFalse();
        assertThat(countsByUserId(userId)).allSatisfy((entity, count) -> assertThat(count).as(entity).isZero());
        assertThat(counts(OTHER_EMAIL)).isEqualTo(otherBefore);
        send(get("/api/categories"), otherToken, null).andExpect(status().isOk());
    }

    @Test
    void delete_endsSessionsImmediately() throws Exception {
        send(delete("/api/account"), access(pair), Map.of("password", PASSWORD))
                .andExpect(status().isNoContent());

        // O access token ainda não expirou, mas a conta já não existe.
        send(get("/api/categories"), access(pair), null).andExpect(status().isUnauthorized());
        send(post("/api/auth/refresh"), null, Map.of("refreshToken", pair.get("refreshToken").asText()))
                .andExpect(status().isBadRequest());
        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void delete_thenSameEmailCanRegisterAgainFromScratch() throws Exception {
        send(delete("/api/account"), access(pair), Map.of("password", PASSWORD))
                .andExpect(status().isNoContent());

        String fresh = register(EMAIL).get("accessToken").asText();
        JsonNode transactions = json(send(get("/api/transactions?month=2026-09"), fresh, null)
                .andExpect(status().isOk()));
        assertThat(transactions).isEmpty();
    }

    @Test
    void delete_sharesPasswordLimitWithChangePassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            send(put("/api/account/password"), access(pair),
                    Map.of("currentPassword", "password-errada-123", "newPassword", "outra-senha-muito-boa-7"))
                    .andExpect(status().isBadRequest());
        }

        // Bloqueado mesmo com a password certa, e a conta fica.
        send(delete("/api/account"), access(pair), Map.of("password", PASSWORD))
                .andExpect(status().isTooManyRequests());
        assertThat(userExists(EMAIL)).isTrue();
    }

    // Categoria com cor, regra, duas importações e uma transação categorizada pela regra.
    private void seed(String token, String suffix) throws Exception {
        long category = json(send(post("/api/categories"), token, Map.of("name", "Extra " + suffix, "color", "#1F77B4"))
                .andExpect(status().isOk())).get("id").asLong();
        send(post("/api/rules"), token, Map.of("keyword", "loja " + suffix.toLowerCase(), "categoryId", category))
                .andExpect(status().isOk());
        for (String description : List.of("Loja " + suffix, "Ordenado " + suffix)) {
            send(post("/api/imports/confirm"), token, Map.of(
                    "filename", "extrato.csv",
                    "transactions", List.of(Map.of("date", "2026-09-05", "description", description, "amount", -10.0))))
                    .andExpect(status().isOk());
        }
    }

    private Map<String, Long> counts(String email) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String entity : OWNED) {
            counts.put(entity, entityManager.createQuery(
                            "SELECT COUNT(x) FROM " + entity + " x WHERE x.user.email = :email", Long.class)
                    .setParameter("email", email)
                    .getSingleResult());
        }
        return counts;
    }

    // Pelo id (coluna user_id, sem join): apanha também linhas que tivessem
    // ficado a apontar para um utilizador já apagado.
    private Map<String, Long> countsByUserId(long userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String entity : OWNED) {
            counts.put(entity, entityManager.createQuery(
                            "SELECT COUNT(x) FROM " + entity + " x WHERE x.user.id = :id", Long.class)
                    .setParameter("id", userId)
                    .getSingleResult());
        }
        return counts;
    }

    private boolean userExists(String email) {
        return entityManager.createQuery("SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                .setParameter("email", email)
                .getSingleResult() > 0;
    }

    private JsonNode register(String email) throws Exception {
        return json(send(post("/api/auth/register"), null, Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isOk()));
    }

    private ResultActions login(String email, String password) throws Exception {
        return send(post("/api/auth/login"), null, Map.of("email", email, "password", password, "rememberMe", true));
    }

    private static String access(JsonNode pair) {
        return pair.get("accessToken").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
