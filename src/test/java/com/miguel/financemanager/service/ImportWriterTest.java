package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.ConfirmImportRequest;
import com.miguel.financemanager.dto.ConfirmTransactionRequest;
import com.miguel.financemanager.dto.ImportSummaryResponse;
import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.CategorizationRule;
import com.miguel.financemanager.entity.StatementImport;
import com.miguel.financemanager.entity.Transaction;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.exception.DuplicateResolutionException;
import com.miguel.financemanager.repository.CategorizationRuleRepository;
import com.miguel.financemanager.repository.CategoryRepository;
import com.miguel.financemanager.repository.StatementImportRepository;
import com.miguel.financemanager.repository.TransactionRepository;
import com.miguel.financemanager.service.parsing.Bank;
import com.miguel.financemanager.service.util.TransactionFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportWriterTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private StatementImportRepository statementImportRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CategorizationRuleRepository ruleRepository;

    @InjectMocks
    private ImportWriter importWriter;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("miguel@teste.com").passwordHash("hash").build();
    }

    @Test
    void write_appliesFirstMatchingRuleByPriorityAndFallsBackToOutros() {
        Category transporte = Category.builder().id(2L).user(user).name("Transporte").isDefault(false).build();
        Category outros = Category.builder().id(6L).user(user).name("Outros").isDefault(false).build();

        CategorizationRule ruleUber = CategorizationRule.builder()
                .id(1L).user(user).keyword("uber").category(transporte).priority(0).build();

        when(ruleRepository.findByUserOrderByPriorityAsc(user)).thenReturn(List.of(ruleUber));
        when(categoryRepository.findByUserAndName(user, "Outros")).thenReturn(Optional.of(outros));
        when(statementImportRepository.save(any(StatementImport.class))).thenAnswer(invocation -> {
            StatementImport si = invocation.getArgument(0);
            si.setId(10L);
            return si;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmTransactionRequest matching = new ConfirmTransactionRequest();
        matching.setDate(LocalDate.of(2026, 9, 1));
        matching.setDescription("Uber viagem centro");
        matching.setAmount(new BigDecimal("-8.50"));

        ConfirmTransactionRequest unmatched = new ConfirmTransactionRequest();
        unmatched.setDate(LocalDate.of(2026, 9, 3));
        unmatched.setDescription("Renda casa");
        unmatched.setAmount(new BigDecimal("-500.00"));

        ConfirmImportRequest request = new ConfirmImportRequest();
        request.setFilename("extrato.csv");
        request.setTransactions(List.of(matching, unmatched));

        ImportSummaryResponse response = importWriter.write(user, request);

        assertThat(response.getTransactionsSaved()).isEqualTo(2);
        assertThat(response.getImportId()).isEqualTo(10L);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());

        List<Transaction> saved = captor.getAllValues();
        assertThat(saved.get(0).getCategory()).isEqualTo(transporte);
        assertThat(saved.get(1).getCategory()).isEqualTo(outros);
    }

    @Test
    void write_fallsBackToSemCategoriaWhenOutrosDoesNotExist() {
        Category semCategoria = Category.builder().id(7L).user(user).name("Sem Categoria").isDefault(true).build();

        when(ruleRepository.findByUserOrderByPriorityAsc(user)).thenReturn(List.of());
        when(categoryRepository.findByUserAndName(user, "Outros")).thenReturn(Optional.empty());
        when(categoryRepository.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.of(semCategoria));
        when(statementImportRepository.save(any(StatementImport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmTransactionRequest t = new ConfirmTransactionRequest();
        t.setDate(LocalDate.of(2026, 9, 3));
        t.setDescription("Renda casa");
        t.setAmount(new BigDecimal("-500.00"));

        ConfirmImportRequest request = new ConfirmImportRequest();
        request.setFilename("extrato.csv");
        request.setTransactions(List.of(t));

        importWriter.write(user, request);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(semCategoria);
    }

    @Test
    void write_throwsWhenNoFallbackCategoryExistsAtAll() {
        when(categoryRepository.findByUserAndName(user, "Outros")).thenReturn(Optional.empty());
        when(categoryRepository.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.empty());
        when(statementImportRepository.save(any(StatementImport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmImportRequest request = new ConfirmImportRequest();
        request.setFilename("extrato.csv");
        request.setTransactions(List.of());

        assertThat(catchException(() -> importWriter.write(user, request)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fallback");
    }

    @Test
    void write_skipsMovementsWhoseHashAlreadyExists() {
        stubConfirmDependencies();
        ConfirmTransactionRequest existing = confirmTx(LocalDate.of(2026, 9, 1), "Renda casa", "-500.00");
        ConfirmTransactionRequest fresh = confirmTx(LocalDate.of(2026, 9, 2), "Uber", "-8.50");
        String existingHash = TransactionFingerprint.compute(1L, LocalDate.of(2026, 9, 1), null, "Renda casa",
                new BigDecimal("-500.00"), null, 0);
        when(transactionRepository.existsByUserAndHash(eq(user), anyString()))
                .thenAnswer(invocation -> existingHash.equals(invocation.getArgument(1)));

        ImportSummaryResponse response = importWriter.write(user, confirmRequest(existing, fresh));

        assertThat(response.getTransactionsSaved()).isEqualTo(1);
        assertThat(response.getDuplicatesSkipped()).isEqualTo(1);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("Uber");
    }

    @Test
    void write_savesIdenticalMovementsWithDifferentOccurrences() {
        stubConfirmDependencies();
        ConfirmTransactionRequest first = confirmTx(LocalDate.of(2026, 9, 1), "Cafe", "-1.20");
        ConfirmTransactionRequest second = confirmTx(LocalDate.of(2026, 9, 1), "Cafe", "-1.20");
        second.setOccurrence(1);

        ImportSummaryResponse response = importWriter.write(user, confirmRequest(first, second));

        assertThat(response.getTransactionsSaved()).isEqualTo(2);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Transaction::getHash).doesNotHaveDuplicates();
    }

    @Test
    void write_rejectsSameHashTwiceInOneRequest() {
        stubConfirmDependencies();
        ConfirmTransactionRequest first = confirmTx(LocalDate.of(2026, 9, 1), "Cafe", "-1.20");
        ConfirmTransactionRequest copy = confirmTx(LocalDate.of(2026, 9, 1), "Cafe", "-1.20");

        assertThat(catchException(() -> importWriter.write(user, confirmRequest(first, copy))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repetido");
    }

    @Test
    void write_storesCgdFieldsAndBank() {
        stubConfirmDependencies();
        ConfirmTransactionRequest t = confirmTx(LocalDate.of(2026, 8, 1), "COMPRAS C.DEB CAFE", "-1.20");
        t.setMovementDate(LocalDate.of(2026, 8, 2));
        t.setBalanceAfter(new BigDecimal("1048.80"));
        ConfirmImportRequest request = confirmRequest(t);
        request.setBank(Bank.CGD);

        importWriter.write(user, request);

        ArgumentCaptor<StatementImport> importCaptor = ArgumentCaptor.forClass(StatementImport.class);
        verify(statementImportRepository).save(importCaptor.capture());
        assertThat(importCaptor.getValue().getBank()).isEqualTo(Bank.CGD);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(captor.getValue().getMovementDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("1048.80");
    }

    @Test
    void write_defaultsBankToGeneric() {
        stubConfirmDependencies();

        importWriter.write(user, confirmRequest(confirmTx(LocalDate.of(2026, 9, 1), "Uber", "-8.50")));

        ArgumentCaptor<StatementImport> captor = ArgumentCaptor.forClass(StatementImport.class);
        verify(statementImportRepository).save(captor.capture());
        assertThat(captor.getValue().getBank()).isEqualTo(Bank.GENERIC);
    }

    @Test
    void write_matchesRulesByContainmentIgnoringCaseAccentsAndSpacing() {
        Category cafes = Category.builder().id(3L).user(user).name("Cafés").isDefault(false).build();
        CategorizationRule rule = CategorizationRule.builder()
                .id(1L).user(user).keyword("café  central").category(cafes).priority(0).build();
        stubConfirmDependencies(List.of(rule));

        importWriter.write(user, confirmRequest(
                confirmTx(LocalDate.of(2026, 8, 1), "COMPRAS C.DEB CAFE CENTRAL", "-1.20")));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(cafes);
        // A descrição gravada é a original; a normalização só serve para comparar.
        assertThat(captor.getValue().getDescription()).isEqualTo("COMPRAS C.DEB CAFE CENTRAL");
    }

    @Test
    void write_ruleCanTargetBankPrefix() {
        Category mbway = Category.builder().id(4L).user(user).name("MB Way").isDefault(false).build();
        CategorizationRule rule = CategorizationRule.builder()
                .id(1L).user(user).keyword("trf mbway").category(mbway).priority(0).build();
        stubConfirmDependencies(List.of(rule));

        importWriter.write(user, confirmRequest(confirmTx(LocalDate.of(2026, 8, 1), "Trf Mbway João", "20.00")));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(mbway);
    }

    @Test
    void write_forcedDuplicateUsesNextOccurrenceAfterExistingMaximum() {
        stubConfirmDependencies();
        LocalDate day = LocalDate.of(2026, 9, 1);
        String occ0 = TransactionFingerprint.compute(1L, day, null, "Cafe", new BigDecimal("-1.20"), null, 0);
        String occ1 = TransactionFingerprint.compute(1L, day, null, "Cafe", new BigDecimal("-1.20"), null, 1);
        when(transactionRepository.existsByUserAndHash(user, occ0)).thenReturn(true);
        when(transactionRepository.findHashesWithSameFields(eq(user), eq(day), eq(null), eq("Cafe"), any(), eq(null)))
                .thenReturn(List.of(occ0, occ1));
        ConfirmTransactionRequest forced = confirmTx(day, "Cafe", "-1.20");
        forced.setAllowDuplicate(true);

        ImportSummaryResponse response = importWriter.write(user, confirmRequest(forced));

        assertThat(response.getImportedAsDuplicate()).isEqualTo(1);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getHash())
                .isEqualTo(TransactionFingerprint.compute(1L, day, null, "Cafe", new BigDecimal("-1.20"), null, 2));
    }

    @Test
    void write_forcedDuplicateFailsWhenStoredHashesAreNotRecognised() {
        stubConfirmDependencies();
        LocalDate day = LocalDate.of(2026, 9, 1);
        String occ0 = TransactionFingerprint.compute(1L, day, null, "Cafe", new BigDecimal("-1.20"), null, 0);
        when(transactionRepository.existsByUserAndHash(user, occ0)).thenReturn(true);
        when(transactionRepository.findHashesWithSameFields(eq(user), eq(day), eq(null), eq("Cafe"), any(), eq(null)))
                .thenReturn(List.of(occ0, "desconhecido"));
        ConfirmTransactionRequest forced = confirmTx(day, "Cafe", "-1.20");
        forced.setAllowDuplicate(true);

        assertThat(catchException(() -> importWriter.write(user, confirmRequest(forced))))
                .isInstanceOf(DuplicateResolutionException.class)
                .hasMessageContaining("só 1 correspondem");
        verify(transactionRepository, org.mockito.Mockito.never()).save(any(Transaction.class));
    }

    private void stubConfirmDependencies() {
        stubConfirmDependencies(List.of());
    }

    private void stubConfirmDependencies(List<CategorizationRule> rules) {
        Category outros = Category.builder().id(6L).user(user).name("Outros").isDefault(false).build();
        when(ruleRepository.findByUserOrderByPriorityAsc(user)).thenReturn(rules);
        when(categoryRepository.findByUserAndName(user, "Outros")).thenReturn(Optional.of(outros));
        when(statementImportRepository.save(any(StatementImport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static ConfirmTransactionRequest confirmTx(LocalDate date, String description, String amount) {
        ConfirmTransactionRequest t = new ConfirmTransactionRequest();
        t.setDate(date);
        t.setDescription(description);
        t.setAmount(new BigDecimal(amount));
        return t;
    }

    private static ConfirmImportRequest confirmRequest(ConfirmTransactionRequest... transactions) {
        ConfirmImportRequest request = new ConfirmImportRequest();
        request.setFilename("extrato.csv");
        request.setTransactions(List.of(transactions));
        return request;
    }

    // Pequeno helper para capturar a exceção sem precisar do assertThatThrownBy
    // em métodos que declaram "throws" (evita ruído de try/catch repetido).
    private static Throwable catchException(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowable(callable);
    }
}
