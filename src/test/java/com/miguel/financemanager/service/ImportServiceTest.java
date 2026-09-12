package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.ConfirmImportRequest;
import com.miguel.financemanager.dto.ConfirmTransactionRequest;
import com.miguel.financemanager.dto.ImportSummaryResponse;
import com.miguel.financemanager.dto.ParseImportResponse;
import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.CategorizationRule;
import com.miguel.financemanager.entity.StatementImport;
import com.miguel.financemanager.entity.Transaction;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.CategorizationRuleRepository;
import com.miguel.financemanager.repository.CategoryRepository;
import com.miguel.financemanager.repository.StatementImportRepository;
import com.miguel.financemanager.repository.TransactionRepository;
import com.miguel.financemanager.service.parsing.ParsedTransaction;
import com.miguel.financemanager.service.parsing.StatementParser;
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
        when(statementParser.parse(file)).thenReturn(parsed);
        when(transactionRepository.existsByUserAndHash(eq(user), anyString())).thenReturn(true);
        when(file.getOriginalFilename()).thenReturn("extrato.csv");

        ParseImportResponse response = importService.parseStatement(file);

        assertThat(response.getFilename()).isEqualTo("extrato.csv");
        assertThat(response.getTransactions()).hasSize(1);
        assertThat(response.getTransactions().get(0).isDuplicate()).isTrue();
    }

    @Test
    void parseStatement_marksNewHashAsNotDuplicate() throws IOException {
        List<ParsedTransaction> parsed = List.of(
                new ParsedTransaction(LocalDate.of(2026, 9, 1), "Uber viagem", new BigDecimal("-8.50"))
        );
        when(statementParser.parse(file)).thenReturn(parsed);
        when(transactionRepository.existsByUserAndHash(eq(user), anyString())).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("extrato.csv");

        ParseImportResponse response = importService.parseStatement(file);

        assertThat(response.getTransactions().get(0).isDuplicate()).isFalse();
    }

    @Test
    void parseStatement_wrapsIOExceptionAsIllegalArgument() throws IOException {
        when(statementParser.parse(file)).thenThrow(new IOException("CSV inválido"));

        assertThat(catchException(() -> importService.parseStatement(file)))
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

    // Pequeno helper para capturar a exceção sem precisar do assertThatThrownBy
    // em métodos que declaram "throws" (evita ruído de try/catch repetido).
    private static Throwable catchException(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowable(callable);
    }
}
