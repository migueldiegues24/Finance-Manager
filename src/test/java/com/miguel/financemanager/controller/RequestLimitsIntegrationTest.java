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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Limites de tamanho: corpo JSON (32 KB / 2 MB no confirm), movimentos por
// extrato (5000) e campos de texto. Os limites do multipart, os corpos sem
// Content-Length (chunked, lentos) e o multipart enviado a outro endpoint
// precisam de um Tomcat real: estão no RequestLimitsServerTest.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RequestLimitsIntegrationTest {

    private static final int KB = 1024;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String token;

    @BeforeEach
    void registerUser() throws Exception {
        MvcResult result = postJson("/api/auth/register",
                objectMapper.writeValueAsString(Map.of("email", "limites@teste.com", "password", "password123")), null)
                .andExpect(status().isOk()).andReturn();
        token = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private ResultActions postJson(String path, String body, String bearer) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mockMvc.perform(request);
    }

    // JSON válido com exatamente `bytes` bytes (um campo extra ignorado enche o resto).
    private static String jsonOfSize(int bytes) {
        String prefix = "{\"email\":\"a@teste.com\",\"password\":\"x\",\"pad\":\"";
        String suffix = "\"}";
        return prefix + "p".repeat(bytes - prefix.length() - suffix.length()) + suffix;
    }

    // --- Corpo geral: 32 KB ---

    @Test
    void publicEndpoint_bodyOver32KB_is413WithJsonMessage() throws Exception {
        String body = jsonOfSize(32 * KB + 1);

        postJson("/api/auth/login", body, null)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.error").value("O pedido é demasiado grande (máximo 32 KB)."));
    }

    @Test
    void bodyOfExactly32KB_isNotRejectedBySize() throws Exception {
        postJson("/api/auth/login", jsonOfSize(32 * KB), null).andExpect(status().isUnauthorized());
    }

    // O limite aplica-se antes da autenticação: sem token, um corpo grande
    // num endpoint protegido dá 413, não chega a ser lido.
    @Test
    void protectedEndpoint_bodyOver32KB_is413EvenWithoutToken() throws Exception {
        postJson("/api/categories", jsonOfSize(40 * KB), null).andExpect(status().isContentTooLarge());
    }

    // Os maiores pedidos normais que existem hoje, com todos os campos no
    // máximo que a validação aceita, ficam muito abaixo dos 32 KB.
    @Test
    void largestExistingPayloads_stayWellUnder32KB() throws Exception {
        // Parte local no máximo do @Email (64) e domínio com etiquetas de 60.
        String longEmail = "u".repeat(64) + "@" + "d".repeat(60) + "." + "e".repeat(60) + ".com";
        // 72: o máximo que o BCrypt aceita (acima disso o registo já dá 400 hoje).
        String register = objectMapper.writeValueAsString(Map.of("email", longEmail, "password", "p".repeat(72)));
        String login = register;
        String category = objectMapper.writeValueAsString(Map.of("name", "c".repeat(100), "color", "#1F5E6B"));
        String rule = objectMapper.writeValueAsString(Map.of("keyword", "k".repeat(255), "categoryId", 1, "priority", 1));

        for (String body : List.of(register, login, category, rule)) {
            assertThat(body.getBytes(StandardCharsets.UTF_8).length).isLessThan(2 * KB);
        }

        postJson("/api/auth/register", register, null).andExpect(status().isOk());
        postJson("/api/auth/login", login, null).andExpect(status().isOk());
        long categoryId = objectMapper.readTree(postJson("/api/categories", category, token)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(put("/api/categories/" + categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(category.replace("#1F5E6B", "#000000")))
                .andExpect(status().isOk());
    }

    @Test
    void malformedJson_is400WithJsonMessage() throws Exception {
        postJson("/api/auth/login", "{\"email\":", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Pedido inválido."));
    }

    // --- Confirm: 2 MB e 5000 movimentos ---

    private static List<Map<String, Object>> rows(int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(Map.of(
                    "date", "2026-09-" + String.format("%02d", i % 28 + 1),
                    "movementDate", "2026-09-" + String.format("%02d", i % 28 + 1),
                    "description", "COMPRA 1234 SUPERMERCADO CONTINENTE LISBOA " + i,
                    "amount", -(1 + i % 500) - 0.25,
                    "balanceAfter", 1000 + i + 0.5,
                    "occurrence", 0,
                    "allowDuplicate", false));
        }
        return rows;
    }

    private String confirmBody(List<Map<String, Object>> rows) throws Exception {
        return objectMapper.writeValueAsString(Map.of("filename", "extrato.csv", "transactions", rows));
    }

    @Test
    void confirm_acceptsBodiesOver32KB() throws Exception {
        String body = confirmBody(rows(536));
        assertThat(body.length()).isGreaterThan(32 * KB);

        postJson("/api/imports/confirm", body, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsSaved").value(536));
    }

    @Test
    void confirm_5000Movements_fitUnder2MB_andAreAccepted() throws Exception {
        String body = confirmBody(rows(5000));
        assertThat(body.getBytes(StandardCharsets.UTF_8).length).isLessThan(2 * KB * KB);

        postJson("/api/imports/confirm", body, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsSaved").value(5000));
    }

    @Test
    void confirm_over5000Movements_is413() throws Exception {
        postJson("/api/imports/confirm", confirmBody(rows(5001)), token)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.error").value("O extrato tem demasiados movimentos (máximo 5000)."));
    }

    @Test
    void confirm_bodyOver2MB_is413() throws Exception {
        String body = "{\"filename\":\"x.csv\",\"transactions\":[],\"pad\":\"" + "p".repeat(2 * KB * KB) + "\"}";

        postJson("/api/imports/confirm", body, token)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.error").value("O pedido é demasiado grande (máximo 2 MB)."));
    }

    // --- Campos de texto: iguais às colunas ---

    @Test
    void overLongTextFields_are400_notDatabaseConflict() throws Exception {
        List<Map<String, Object>> row = List.of(Map.of("date", "2026-09-01", "description", "d".repeat(501), "amount", -1));
        postJson("/api/imports/confirm", objectMapper.writeValueAsString(
                Map.of("filename", "extrato.csv", "transactions", row)), token)
                .andExpect(status().isBadRequest());

        List<Map<String, Object>> ok = List.of(Map.of("date", "2026-09-01", "description", "d", "amount", -1));
        postJson("/api/imports/confirm", objectMapper.writeValueAsString(
                Map.of("filename", "f".repeat(256), "transactions", ok)), token)
                .andExpect(status().isBadRequest());

        postJson("/api/rules", objectMapper.writeValueAsString(
                Map.of("keyword", "k".repeat(256), "categoryId", 1)), token)
                .andExpect(status().isBadRequest());
    }

    // --- Upload: 5000 movimentos, e o filtro JSON não se aplica ---

    private static byte[] genericCsv(int rows) {
        StringBuilder csv = new StringBuilder("data,descricao,valor\n");
        for (int i = 0; i < rows; i++) {
            csv.append("2026-09-").append(String.format("%02d", i % 28 + 1))
                    .append(",COMPRA SUPERMERCADO ").append(i).append(",-").append(1 + i % 90).append(".50\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private ResultActions upload(byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/imports/parse")
                .file(new MockMultipartFile("file", "extrato.csv", "text/csv", content))
                .header("Authorization", "Bearer " + token));
    }

    // Um upload bem acima de 32 KB passa: o multipart está isento do filtro
    // do corpo JSON e só tem os limites próprios (1 MB).
    @Test
    void upload_over32KB_isNotLimitedByJsonBodyFilter() throws Exception {
        byte[] csv = genericCsv(2000);
        assertThat(csv.length).isGreaterThan(64 * KB);

        upload(csv).andExpect(status().isOk()).andExpect(jsonPath("$.transactions.length()").value(2000));
    }

    @Test
    void upload_5000Movements_accepted_5001Rejected() throws Exception {
        upload(genericCsv(5000)).andExpect(status().isOk());

        upload(genericCsv(5001))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.error").value("O extrato tem demasiados movimentos (máximo 5000)."));
    }
}
