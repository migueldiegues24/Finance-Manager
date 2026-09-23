package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miguel.financemanager.entity.StatementImport;
import com.miguel.financemanager.entity.Transaction;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.CategoryRepository;
import com.miguel.financemanager.repository.StatementImportRepository;
import com.miguel.financemanager.repository.TransactionRepository;
import com.miguel.financemanager.repository.UserRepository;
import com.miguel.financemanager.service.util.TransactionFingerprint;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReimportIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private StatementImportRepository statementImportRepository;
    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        token = register("reimport@teste.com");
        user = userRepository.findByEmail("reimport@teste.com").orElseThrow();
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "senha-de-teste-42"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static Map<String, Object> cafe(int occurrence, boolean allowDuplicate) {
        Map<String, Object> row = new HashMap<>();
        row.put("date", DAY.toString());
        row.put("description", "Cafe");
        row.put("amount", -1.20);
        row.put("occurrence", occurrence);
        row.put("allowDuplicate", allowDuplicate);
        return row;
    }

    private ResultActions confirm(String accessToken, List<Map<String, Object>> rows) throws Exception {
        return mockMvc.perform(post("/api/imports/confirm")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("filename", "extrato.csv", "transactions", rows))));
    }

    private JsonNode confirmOk(String accessToken, List<Map<String, Object>> rows) throws Exception {
        MvcResult result = confirm(accessToken, rows).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String cafeHash(User owner, int occurrence) {
        return TransactionFingerprint.compute(owner.getId(), DAY, null, "Cafe", new BigDecimal("-1.20"), null, occurrence);
    }

    private Set<String> storedHashes(User owner) {
        entityManager.flush();
        entityManager.clear();
        return transactionRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(owner.getId()))
                .map(Transaction::getHash)
                .collect(Collectors.toSet());
    }

    // ---------- Ida e volta: o hash recalculado a partir da BD é o guardado ----------

    @Test
    void storedRowsRecomputeToTheSameHash() throws Exception {
        List<Map<String, Object>> rows = List.of(
                Map.of("date", "2026-09-01", "description", "Uber viagem", "amount", -8.5),
                Map.of("date", "2026-09-02", "description", "Salário", "amount", 1200),
                Map.of("date", "2026-09-03", "description", "Juros", "amount", 0.10),
                Map.of("date", "2026-09-04", "description", "  Café   Açúcar  Pão ", "amount", -45.30),
                Map.of("date", "2026-09-04", "movementDate", "2026-09-05", "description", "COMPRAS C.DEB ÓTICA",
                        "amount", -12.0, "balanceAfter", 1234.5));
        confirmOk(token, rows);

        entityManager.flush();
        entityManager.clear();
        List<Transaction> stored = transactionRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(user.getId()))
                .toList();

        assertThat(stored).hasSize(rows.size());
        for (Transaction t : stored) {
            String recomputed = TransactionFingerprint.compute(user.getId(), t.getTransactionDate(),
                    t.getMovementDate(), t.getDescription(), t.getAmount(), t.getBalanceAfter(), 0);
            assertThat(recomputed).as("hash de \"%s\"", t.getDescription()).isEqualTo(t.getHash());
        }
    }

    @Test
    void amountWithMoreThanTwoDecimalsIsRejected() throws Exception {
        confirm(token, List.of(Map.of("date", "2026-09-01", "description", "X", "amount", -1.235)))
                .andExpect(status().isBadRequest());
    }

    // ---------- Reimportação ----------

    @Test
    void reimportWithoutAllowDuplicateSkipsExistingRow() throws Exception {
        confirmOk(token, List.of(cafe(0, false)));

        JsonNode summary = confirmOk(token, List.of(cafe(0, false)));

        assertThat(summary.get("transactionsSaved").asInt()).isZero();
        assertThat(summary.get("duplicatesSkipped").asInt()).isEqualTo(1);
        assertThat(summary.get("importedAsDuplicate").asInt()).isZero();
        assertThat(storedHashes(user)).containsExactly(cafeHash(user, 0));
    }

    @Test
    void reimportWithAllowDuplicateSavesNextOccurrence() throws Exception {
        confirmOk(token, List.of(cafe(0, false)));

        JsonNode summary = confirmOk(token, List.of(cafe(0, true)));

        assertThat(summary.get("transactionsSaved").asInt()).isEqualTo(1);
        assertThat(summary.get("duplicatesSkipped").asInt()).isZero();
        assertThat(summary.get("importedAsDuplicate").asInt()).isEqualTo(1);
        assertThat(storedHashes(user)).containsExactlyInAnyOrder(cafeHash(user, 0), cafeHash(user, 1));
    }

    @Test
    void repeatedForcedReimportsKeepIncreasingTheOccurrence() throws Exception {
        confirmOk(token, List.of(cafe(0, false)));
        confirmOk(token, List.of(cafe(0, true)));
        confirmOk(token, List.of(cafe(0, true)));

        assertThat(storedHashes(user))
                .containsExactlyInAnyOrder(cafeHash(user, 0), cafeHash(user, 1), cafeHash(user, 2));
    }

    @Test
    void forcedDuplicateGoesAboveTheMaximumEvenWithGaps() throws Exception {
        // Ocorrências 0 e 2 importadas, 1 não: o máximo é 2, não o primeiro livre.
        confirmOk(token, List.of(cafe(0, false), cafe(2, false)));

        confirmOk(token, List.of(cafe(0, true)));

        assertThat(storedHashes(user))
                .containsExactlyInAnyOrder(cafeHash(user, 0), cafeHash(user, 2), cafeHash(user, 3));
    }

    @Test
    void allowDuplicateOnRowThatDoesNotExistIsIgnored() throws Exception {
        JsonNode summary = confirmOk(token, List.of(cafe(0, true)));

        assertThat(summary.get("transactionsSaved").asInt()).isEqualTo(1);
        assertThat(summary.get("importedAsDuplicate").asInt()).isZero();
        assertThat(storedHashes(user)).containsExactly(cafeHash(user, 0));
    }

    @Test
    void otherUsersRowsWithSameFieldsDoNotCountForTheMaximum() throws Exception {
        String otherToken = register("outro@teste.com");
        User other = userRepository.findByEmail("outro@teste.com").orElseThrow();
        confirmOk(otherToken, List.of(cafe(0, false), cafe(1, false), cafe(2, false)));
        confirmOk(token, List.of(cafe(0, false)));

        confirmOk(token, List.of(cafe(0, true)));

        assertThat(storedHashes(user)).containsExactlyInAnyOrder(cafeHash(user, 0), cafeHash(user, 1));
        assertThat(storedHashes(other))
                .containsExactlyInAnyOrder(cafeHash(other, 0), cafeHash(other, 1), cafeHash(other, 2));
    }

    @Test
    void forcedDuplicateDoesNotTakeTheHashOfALaterNewRowInTheSameRequest() throws Exception {
        confirmOk(token, List.of(cafe(0, false)));

        // A ocorrência 1 é um movimento novo legítimo que vem depois no pedido.
        JsonNode summary = confirmOk(token, List.of(cafe(0, true), cafe(1, false)));

        assertThat(summary.get("transactionsSaved").asInt()).isEqualTo(2);
        assertThat(summary.get("importedAsDuplicate").asInt()).isEqualTo(1);
        assertThat(storedHashes(user))
                .containsExactlyInAnyOrder(cafeHash(user, 0), cafeHash(user, 1), cafeHash(user, 2));
    }

    @Test
    void sameHashTwiceInOneRequestIsStillRejected() throws Exception {
        confirm(token, List.of(cafe(0, true), cafe(0, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("repetido")));
    }

    @Test
    void unrecognisedStoredHashFailsClearlyInsteadOfGuessing() throws Exception {
        confirmOk(token, List.of(cafe(0, false)));
        // Linha com os mesmos campos mas um hash que nenhuma ocorrência produz.
        StatementImport statementImport = statementImportRepository.save(
                StatementImport.builder().user(user).filename("manual.csv").build());
        transactionRepository.save(Transaction.builder()
                .user(user)
                .statementImport(statementImport)
                .transactionDate(DAY)
                .description("Cafe")
                .amount(new BigDecimal("-1.20"))
                .category(categoryRepository.findByUserAndName(user, "Outros").orElseThrow())
                .hash("hash-que-nao-corresponde")
                .build());

        confirm(token, List.of(cafe(0, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("só 1 correspondem")));
    }
}
