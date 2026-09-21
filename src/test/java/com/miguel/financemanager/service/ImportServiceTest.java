package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.ConfirmImportRequest;
import com.miguel.financemanager.dto.ConfirmTransactionRequest;
import com.miguel.financemanager.dto.ImportSummaryResponse;
import com.miguel.financemanager.dto.ParseImportResponse;
import com.miguel.financemanager.dto.ParsedTransactionResponse;
import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.CategorizationRule;
import com.miguel.financemanager.entity.StatementImport;
import com.miguel.financemanager.entity.Transaction;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.CategorizationRuleRepository;
import com.miguel.financemanager.repository.CategoryRepository;
import com.miguel.financemanager.repository.StatementImportRepository;
import com.miguel.financemanager.repository.TransactionRepository;
import com.miguel.financemanager.service.parsing.Bank;
import com.miguel.financemanager.service.parsing.ParsedTransaction;
import com.miguel.financemanager.service.parsing.StatementParser;
import com.miguel.financemanager.service.parsing.StatementParserRegistry;
import com.miguel.financemanager.service.util.TransactionFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
class ImportServiceTest {

    @Mock
    private StatementParserRegistry parserRegistry;
    @Mock
    private StatementParser statementParser;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private StatementImportRepository statementImportRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CategorizationRuleRepository ruleRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private MultipartFile file;

    @InjectMocks
    private ImportService importService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("miguel@teste.com").passwordHash("hash").build();
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void parseStatement_marksExistingHashAsDuplicate() throws IOException {
        List<ParsedTransaction> parsed = List.of(
                new ParsedTransaction(LocalDate.of(2026, 9, 1), "Uber viagem", new BigDecimal("-8.50"))
        );
        when(parserRegistry.forBank(Bank.GENERIC)).thenReturn(statementParser);
        when(statementParser.parse(file)).thenReturn(parsed);
        when(transactionRepository.existsByUserAndHash(eq(user), anyString())).thenReturn(true);
        when(file.getOriginalFilename()).thenReturn("extrato.csv");

        ParseImportResponse response = importService.parseStatement(file, Bank.GENERIC);

        assertThat(response.getFilename()).isEqualTo("extrato.csv");
        assertThat(response.getTransactions()).hasSize(1);
        assertThat(response.getTransactions().get(0).isDuplicate()).isTrue();
    }

    @Test
    void parseStatement_marksNewHashAsNotDuplicate() throws IOException {
        List<ParsedTransaction> parsed = List.of(
                new ParsedTransaction(LocalDate.of(2026, 9, 1), "Uber viagem", new BigDecimal("-8.50"))
        );
        when(parserRegistry.forBank(Bank.GENERIC)).thenReturn(statementParser);
        when(statementParser.parse(file)).thenReturn(parsed);
        when(transactionRepository.existsByUserAndHash(eq(user), anyString())).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("extrato.csv");

        ParseImportResponse response = importService.parseStatement(file, Bank.GENERIC);

        assertThat(response.getTransactions().get(0).isDuplicate()).isFalse();
    }

    @Test
    void parseStatement_wrapsIOExceptionAsIllegalArgument() throws IOException {
        when(parserRegistry.forBank(Bank.GENERIC)).thenReturn(statementParser);
        when(statementParser.parse(file)).thenThrow(new IOException("CSV inválido"));

        assertThat(catchException(() -> importService.parseStatement(file, Bank.GENERIC)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Não foi possível ler");
    }

    @Test
    void confirmImport_appliesFirstMatchingRuleByPriorityAndFallsBackToOutros() {
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

        ImportSummaryResponse response = importService.confirmImport(request);

        assertThat(response.getTransactionsSaved()).isEqualTo(2);
        assertThat(response.getImportId()).isEqualTo(10L);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());

        List<Transaction> saved = captor.getAllValues();
        assertThat(saved.get(0).getCategory()).isEqualTo(transporte);
        assertThat(saved.get(1).getCategory()).isEqualTo(outros);
    }

    @Test
    void confirmImport_fallsBackToSemCategoriaWhenOutrosDoesNotExist() {
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

        importService.confirmImport(request);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(semCategoria);
    }

    @Test
    void confirmImport_throwsWhenNoFallbackCategoryExistsAtAll() {
        when(categoryRepository.findByUserAndName(user, "Outros")).thenReturn(Optional.empty());
        when(categoryRepository.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.empty());
        when(statementImportRepository.save(any(StatementImport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmImportRequest request = new ConfirmImportRequest();
        request.setFilename("extrato.csv");
        request.setTransactions(List.of());

        assertThat(catchException(() -> importService.confirmImport(request)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fallback");
    }

    @Test
    void parseStatement_givesIdenticalMovementsIncreasingOccurrencesAndDistinctHashes() throws IOException {
        ParsedTransaction coffee = new ParsedTransaction(LocalDate.of(2026, 9, 1), "Cafe", new BigDecimal("-1.20"));
        when(parserRegistry.forBank(Bank.GENERIC)).thenReturn(statementParser);
        when(statementParser.parse(file)).thenReturn(List.of(coffee, coffee, coffee));
        when(transactionRepository.existsByUserAndHash(eq(user), anyString())).thenReturn(false);

        List<ParsedTransactionResponse> result = importService.parseStatement(file, Bank.GENERIC).getTransactions();

        assertThat(result).extracting(ParsedTransactionResponse::getOccurrence).containsExactly(0, 1, 2);
        assertThat(result).extracting(ParsedTransactionResponse::getHash).doesNotHaveDuplicates();
        // A primeira ocorrência mantém a fórmula original do CSV genérico.
        assertThat(result.get(0).getHash()).isEqualTo(TransactionFingerprint.compute(
                1L, LocalDate.of(2026, 9, 1), null, "Cafe", new BigDecimal("-1.20"), null, 0));
    }

    @Test
    void parseStatement_passesMovementDateAndBalanceThrough() throws IOException {
        ParsedTransaction cgd = new ParsedTransaction(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2),
                "COMPRAS C.DEB CAFE", new BigDecimal("-1.20"), new BigDecimal("1048.80"));
        when(parserRegistry.forBank(Bank.CGD)).thenReturn(statementParser);
        when(statementParser.parse(file)).thenReturn(List.of(cgd));
        when(transactionRepository.existsByUserAndHash(eq(user), anyString())).thenReturn(false);

        ParsedTransactionResponse result = importService.parseStatement(file, Bank.CGD).getTransactions().get(0);

        assertThat(result.getMovementDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(result.getBalanceAfter()).isEqualByComparingTo("1048.80");
        assertThat(result.getHash()).isEqualTo(TransactionFingerprint.compute(1L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 2), "COMPRAS C.DEB CAFE", new BigDecimal("-1.20"), new BigDecimal("1048.80"), 0));
    }

    @Test
    void confirmImport_skipsMovementsWhoseHashAlreadyExists() {
        stubConfirmDependencies();
        ConfirmTransactionRequest existing = confirmTx(LocalDate.of(2026, 9, 1), "Renda casa", "-500.00");
        ConfirmTransactionRequest fresh = confirmTx(LocalDate.of(2026, 9, 2), "Uber", "-8.50");
        String existingHash = TransactionFingerprint.compute(1L, LocalDate.of(2026, 9, 1), null, "Renda casa",
                new BigDecimal("-500.00"), null, 0);
        when(transactionRepository.existsByUserAndHash(eq(user), anyString()))
                .thenAnswer(invocation -> existingHash.equals(invocation.getArgument(1)));

        ImportSummaryResponse response = importService.confirmImport(confirmRequest(existing, fresh));

        assertThat(response.getTransactionsSaved()).isEqualTo(1);
        assertThat(response.getDuplicatesSkipped()).isEqualTo(1);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("Uber");
    }

    @Test
    void confirmImport_savesIdenticalMovementsWithDifferentOccurrences() {
        stubConfirmDependencies();
        ConfirmTransactionRequest first = confirmTx(LocalDate.of(2026, 9, 1), "Cafe", "-1.20");
        ConfirmTransactionRequest second = confirmTx(LocalDate.of(2026, 9, 1), "Cafe", "-1.20");
        second.setOccurrence(1);

        ImportSummaryResponse response = importService.confirmImport(confirmRequest(first, second));

        assertThat(response.getTransactionsSaved()).isEqualTo(2);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Transaction::getHash).doesNotHaveDuplicates();
    }

    @Test
    void confirmImport_rejectsSameHashTwiceInOneRequest() {
        stubConfirmDependencies();
        ConfirmTransactionRequest first = confirmTx(LocalDate.of(2026, 9, 1), "Cafe", "-1.20");
        ConfirmTransactionRequest copy = confirmTx(LocalDate.of(2026, 9, 1), "Cafe", "-1.20");

        assertThat(catchException(() -> importService.confirmImport(confirmRequest(first, copy))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repetido");
    }

    @Test
    void confirmImport_storesCgdFieldsAndBank() {
        stubConfirmDependencies();
        ConfirmTransactionRequest t = confirmTx(LocalDate.of(2026, 8, 1), "COMPRAS C.DEB CAFE", "-1.20");
        t.setMovementDate(LocalDate.of(2026, 8, 2));
        t.setBalanceAfter(new BigDecimal("1048.80"));
        ConfirmImportRequest request = confirmRequest(t);
        request.setBank(Bank.CGD);

        importService.confirmImport(request);

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
    void confirmImport_defaultsBankToGeneric() {
        stubConfirmDependencies();

        importService.confirmImport(confirmRequest(confirmTx(LocalDate.of(2026, 9, 1), "Uber", "-8.50")));

        ArgumentCaptor<StatementImport> captor = ArgumentCaptor.forClass(StatementImport.class);
        verify(statementImportRepository).save(captor.capture());
        assertThat(captor.getValue().getBank()).isEqualTo(Bank.GENERIC);
    }

    @Test
    void confirmImport_matchesRulesByContainmentIgnoringCaseAccentsAndSpacing() {
        Category cafes = Category.builder().id(3L).user(user).name("Cafés").isDefault(false).build();
        CategorizationRule rule = CategorizationRule.builder()
                .id(1L).user(user).keyword("café  central").category(cafes).priority(0).build();
        stubConfirmDependencies(List.of(rule));

        importService.confirmImport(confirmRequest(
                confirmTx(LocalDate.of(2026, 8, 1), "COMPRAS C.DEB CAFE CENTRAL", "-1.20")));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(cafes);
        // A descrição gravada é a original; a normalização só serve para comparar.
        assertThat(captor.getValue().getDescription()).isEqualTo("COMPRAS C.DEB CAFE CENTRAL");
    }

    @Test
    void confirmImport_ruleCanTargetBankPrefix() {
        Category mbway = Category.builder().id(4L).user(user).name("MB Way").isDefault(false).build();
        CategorizationRule rule = CategorizationRule.builder()
                .id(1L).user(user).keyword("trf mbway").category(mbway).priority(0).build();
        stubConfirmDependencies(List.of(rule));

        importService.confirmImport(confirmRequest(confirmTx(LocalDate.of(2026, 8, 1), "Trf Mbway João", "20.00")));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(mbway);
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
