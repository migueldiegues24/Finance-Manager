package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.io.IOException;
import java.io.InputStream;
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

    // --- Extratos CGD (ficheiro sintético em src/test/resources/statements) ---

    private static String cgdCsv() throws IOException {
        try (InputStream in = ImportControllerIntegrationTest.class.getResourceAsStream("/statements/cgd_sintetico.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Extrato seguinte, sobreposto ao sintético: 1 movimento novo (linha 16)
    // e os 3 mais recentes do extrato anterior.
    private static String overlappingCgdCsv() throws IOException {
        String header = "Data mov.;Data-valor;Descrição;Montante;Saldo contabilístico após movimento\n";
        String previous = cgdCsv();
        String preamble = previous.substring(0, previous.indexOf("Data mov."));
        return preamble + header
                + "10-08-2026;10-08-2026;COMPRAS C.DEB FARMACIA EXEMPLO;-10,00;2.090,00\n"
                + "08-08-2026;07-08-2026;COMPRAS C.DEB SUPERMERCADO EXEMPLO;-58,71;2.100,00\n"
                + "06-08-2026;06-08-2026;TRF P2P MARIA EXEMPLO;-100,00;2.158,71\n"
                + "05-08-2026;04-08-2026;Pagamento Serviços Água;-23,45;2.258,71\n";
    }

    private JsonNode parseCgd(String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/imports/parse")
                        .file(file)
                        .param("bank", "CGD")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // Reenvia os movimentos devolvidos pelo parse com os mesmos campos que o frontend.
    private MvcResult confirmCgd(JsonNode parsed) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("filename", parsed.get("filename").asText());
        body.put("bank", "CGD");
        ArrayNode transactions = body.putArray("transactions");
        for (JsonNode t : parsed.get("transactions")) {
            ObjectNode tx = transactions.addObject();
            for (String field : List.of("date", "movementDate", "description", "amount", "balanceAfter", "occurrence")) {
                tx.set(field, t.get(field));
            }
        }
        return mockMvc.perform(post("/api/imports/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void parseCgd_returnsChronologicalMovementsWithValueDateMovementDateAndBalance() throws Exception {
        JsonNode parsed = parseCgd("Consulta_de_movimentos.xls", cgdCsv());
        JsonNode transactions = parsed.get("transactions");

        org.assertj.core.api.Assertions.assertThat(transactions.size()).isEqualTo(7);
        JsonNode coffee = transactions.get(1);
        org.assertj.core.api.Assertions.assertThat(coffee.get("date").asText()).isEqualTo("2026-08-01");
        org.assertj.core.api.Assertions.assertThat(coffee.get("movementDate").asText()).isEqualTo("2026-08-02");
        org.assertj.core.api.Assertions.assertThat(coffee.get("balanceAfter").decimalValue())
                .isEqualByComparingTo("1048.80");
        // Os dois cafés só diferem no saldo e não podem colidir.
        org.assertj.core.api.Assertions.assertThat(coffee.get("hash").asText())
                .isNotEqualTo(transactions.get(2).get("hash").asText());
    }

    @Test
    void reimportingOverlappingCgdStatementDoesNotDuplicateMovements() throws Exception {
        MvcResult first = confirmCgd(parseCgd("agosto.csv", cgdCsv()));
        JsonNode firstSummary = objectMapper.readTree(first.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(firstSummary.get("transactionsSaved").asInt()).isEqualTo(7);

        JsonNode overlapping = parseCgd("agosto_2.csv", overlappingCgdCsv());
        List<Boolean> duplicates = new java.util.ArrayList<>();
        overlapping.get("transactions").forEach(t -> duplicates.add(t.get("duplicate").asBoolean()));
        org.assertj.core.api.Assertions.assertThat(duplicates).containsExactly(true, true, true, false);

        // Mesmo que o cliente envie tudo (incluindo os já importados), só o novo é gravado.
        MvcResult second = confirmCgd(overlapping);
        JsonNode secondSummary = objectMapper.readTree(second.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(secondSummary.get("transactionsSaved").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(secondSummary.get("duplicatesSkipped").asInt()).isEqualTo(3);
    }

    @Test
    void parseCgd_rejectsGenericCsvWithClearHeaderError() throws Exception {
        mockMvc.perform(multipart("/api/imports/parse")
                        .file(csvFile())
                        .param("bank", "CGD")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("não parece ser um extrato CGD")));
    }

    @Test
    void parse_rejectsUnknownBank() throws Exception {
        mockMvc.perform(multipart("/api/imports/parse")
                        .file(csvFile())
                        .param("bank", "xpto")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Banco desconhecido")));
    }

    @Test
    void genericCsv_identicalRowsInSameFileAreAllImported() throws Exception {
        String csv = "date,description,amount\n"
                + "2026-09-01,Cafe,-1.20\n"
                + "2026-09-01,Cafe,-1.20\n";
        MockMultipartFile file = new MockMultipartFile("file", "cafes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MvcResult parsed = mockMvc.perform(multipart("/api/imports/parse")
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].occurrence").value(0))
                .andExpect(jsonPath("$.transactions[1].occurrence").value(1))
                .andReturn();

        JsonNode transactions = objectMapper.readTree(parsed.getResponse().getContentAsString()).get("transactions");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("filename", "cafes.csv");
        ArrayNode txs = body.putArray("transactions");
        for (JsonNode t : transactions) {
            ObjectNode tx = txs.addObject();
            for (String field : List.of("date", "description", "amount", "occurrence")) {
                tx.set(field, t.get(field));
            }
        }

        mockMvc.perform(post("/api/imports/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsSaved").value(2))
                .andExpect(jsonPath("$.duplicatesSkipped").value(0));
    }
}
