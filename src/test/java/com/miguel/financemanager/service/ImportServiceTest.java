package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.ConfirmImportRequest;
import com.miguel.financemanager.dto.ImportSummaryResponse;
import com.miguel.financemanager.dto.ParseImportResponse;
import com.miguel.financemanager.dto.ParsedTransactionResponse;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.exception.ImportConflictException;
import com.miguel.financemanager.repository.TransactionRepository;
import com.miguel.financemanager.service.parsing.Bank;
import com.miguel.financemanager.service.parsing.ParsedTransaction;
import com.miguel.financemanager.service.parsing.StatementParser;
import com.miguel.financemanager.service.parsing.StatementParserRegistry;
import com.miguel.financemanager.service.util.TransactionFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private ImportWriter importWriter;
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
    void confirmImport_delegatesToWriterForCurrentUser() {
        ConfirmImportRequest request = new ConfirmImportRequest();
        ImportSummaryResponse summary = new ImportSummaryResponse(1L, "x.csv", 2, 0, 0);
        when(importWriter.write(user, request)).thenReturn(summary);

        assertThat(importService.confirmImport(request)).isSameAs(summary);
        verify(importWriter, times(1)).write(user, request);
    }

    @Test
    void confirmImport_retriesOnceWhenUniqueIndexIsViolated() {
        ConfirmImportRequest request = new ConfirmImportRequest();
        ImportSummaryResponse summary = new ImportSummaryResponse(2L, "x.csv", 1, 1, 0);
        when(importWriter.write(user, request))
                .thenThrow(new DataIntegrityViolationException("uq_transactions_user_hash"))
                .thenReturn(summary);

        assertThat(importService.confirmImport(request)).isSameAs(summary);
        verify(importWriter, times(2)).write(user, request);
    }

    @Test
    void confirmImport_throwsConflictWhenRetryAlsoFails() {
        ConfirmImportRequest request = new ConfirmImportRequest();
        when(importWriter.write(user, request))
                .thenThrow(new DataIntegrityViolationException("uq_transactions_user_hash"));

        assertThat(catchException(() -> importService.confirmImport(request)))
                .isInstanceOf(ImportConflictException.class)
                .hasMessageContaining("Tenta de novo");
        verify(importWriter, times(2)).write(user, request);
    }

    private static Throwable catchException(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowable(callable);
    }
}
