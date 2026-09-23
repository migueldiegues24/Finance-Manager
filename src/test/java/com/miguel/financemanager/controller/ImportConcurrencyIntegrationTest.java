package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miguel.financemanager.dto.ConfirmImportRequest;
import com.miguel.financemanager.dto.ConfirmTransactionRequest;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.TransactionRepository;
import com.miguel.financemanager.repository.UserRepository;
import com.miguel.financemanager.service.ImportWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sem @Transactional: cada pedido precisa de transações próprias e
// confirmadas, como em produção. Por isso as tabelas são limpas no fim.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ImportWriter importWriter;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private TransactionRepository transactionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void cleanTables() {
        jdbc.execute("DELETE FROM transactions");
        jdbc.execute("DELETE FROM statement_imports");
        jdbc.execute("DELETE FROM categorization_rules");
        jdbc.execute("DELETE FROM categories");
        jdbc.execute("DELETE FROM refresh_tokens");
        jdbc.execute("DELETE FROM users");
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "senha-de-teste-42"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static ConfirmTransactionRequest row(String date, String description, String amount) {
        ConfirmTransactionRequest t = new ConfirmTransactionRequest();
        t.setDate(LocalDate.parse(date));
        t.setDescription(description);
        t.setAmount(new BigDecimal(amount));
        return t;
    }

    @Test
    void confirmRetriesAndSkipsRowThatAnotherRequestCommittedInBetween() throws Exception {
        String token = register("race@teste.com");
        User user = userRepository.findByEmail("race@teste.com").orElseThrow();

        // Outro pedido grava "Renda" e confirma entre a verificação de
        // existência e o insert deste pedido: o insert viola o índice único.
        AtomicBoolean interleaved = new AtomicBoolean();
        doAnswer(invocation -> {
            if (interleaved.compareAndSet(false, true)) {
                ConfirmImportRequest other = new ConfirmImportRequest();
                other.setFilename("outro.csv");
                other.setTransactions(List.of(row("2026-09-01", "Renda", "-500.00")));

                TransactionTemplate otherTx = new TransactionTemplate(transactionManager);
                otherTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                otherTx.executeWithoutResult(status -> importWriter.write(user, other));

                // A verificação deste pedido aconteceu antes desse commit.
                return false;
            }
            // Resposta real. O spy envolve um proxy JDK de uma interface, por isso
            // callRealMethod() não está disponível; o JdbcTemplate usa a ligação
            // da transação em curso e vê o mesmo estado que o repositório.
            User owner = invocation.getArgument(0);
            String hash = invocation.getArgument(1);
            return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ? AND hash = ?",
                    Long.class, owner.getId(), hash) > 0;
        }).when(transactionRepository).existsByUserAndHash(any(), anyString());

        String body = objectMapper.writeValueAsString(Map.of(
                "filename", "extrato.csv",
                "transactions", List.of(
                        Map.of("date", "2026-09-01", "description", "Renda", "amount", -500.00),
                        Map.of("date", "2026-09-02", "description", "Uber", "amount", -8.50))));

        mockMvc.perform(post("/api/imports/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsSaved").value(1))
                .andExpect(jsonPath("$.duplicatesSkipped").value(1));

        assertThat(interleaved).isTrue();
        Long userId = user.getId();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND description = 'Renda'", Long.class, userId))
                .isEqualTo(1L);
        // A primeira tentativa foi revertida por inteiro: só ficam a importação
        // do outro pedido e a da segunda tentativa.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM statement_imports WHERE user_id = ?", Long.class, userId)).isEqualTo(2L);
    }

    // Dois confirms reais do mesmo extrato em paralelo: ambos respondem 200
    // e cada movimento fica gravado exatamente uma vez.
    @RepeatedTest(20)
    void twoConcurrentConfirmsOfTheSameStatementSaveEachMovementOnce() throws Exception {
        String token = register("parallel@teste.com");
        int rows = 20;
        List<Map<String, Object>> transactions = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            transactions.add(Map.of("date", "2026-09-01", "description", "Movimento " + i, "amount", -(i + 1)));
        }
        String body = objectMapper.writeValueAsString(Map.of("filename", "extrato.csv", "transactions", transactions));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/imports/confirm")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn();
                }));
            }
            start.countDown();

            int saved = 0;
            int skipped = 0;
            for (Future<MvcResult> future : results) {
                MvcResult result = future.get(30, TimeUnit.SECONDS);
                assertThat(result.getResponse().getStatus()).isEqualTo(200);
                JsonNode summary = objectMapper.readTree(result.getResponse().getContentAsString());
                saved += summary.get("transactionsSaved").asInt();
                skipped += summary.get("duplicatesSkipped").asInt();
            }

            assertThat(saved).isEqualTo(rows);
            assertThat(skipped).isEqualTo(rows);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Long.class)).isEqualTo((long) rows);
        } finally {
            pool.shutdownNow();
        }
    }
}
