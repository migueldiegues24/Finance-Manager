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
import com.miguel.financemanager.service.parsing.ParsedTransaction;
import com.miguel.financemanager.service.parsing.StatementParser;
import com.miguel.financemanager.service.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final StatementParser statementParser;
    private final TransactionRepository transactionRepository;
    private final StatementImportRepository statementImportRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationRuleRepository ruleRepository;
    private final CurrentUserService currentUserService;

    // Não persiste nada. O frontend mostra esta lista numa página de
    // revisão, onde o utilizador remove o que não quer antes de confirmar.
    public ParseImportResponse parseStatement(MultipartFile file) {
        User user = currentUserService.getCurrentUser();

        List<ParsedTransaction> parsed;
        try {
            parsed = statementParser.parse(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível ler o ficheiro: " + e.getMessage());
        }

        List<ParsedTransactionResponse> response = parsed.stream()
                .map(t -> {
                    String hash = computeHash(user, t.date(), t.description(), t.amount());
                    boolean duplicate = transactionRepository.existsByUserAndHash(user, hash);
                    return new ParsedTransactionResponse(t.date(), t.description(), t.amount(), hash, duplicate);
                })
                .toList();

        return new ParseImportResponse(file.getOriginalFilename(), response);
    }

    @Transactional
    public ImportSummaryResponse confirmImport(ConfirmImportRequest request) {
        User user = currentUserService.getCurrentUser();

        StatementImport statementImport = StatementImport.builder()
                .user(user)
                .filename(request.getFilename())
                .build();
        statementImportRepository.save(statementImport);

        List<CategorizationRule> rules = ruleRepository.findByUserOrderByPriorityAsc(user);
        Category fallback = resolveFallbackCategory(user);

        int saved = 0;
        for (ConfirmTransactionRequest t : request.getTransactions()) {
            Category category = resolveCategory(t.getDescription(), rules, fallback);
            String hash = computeHash(user, t.getDate(), t.getDescription(), t.getAmount());

            Transaction transaction = Transaction.builder()
                    .user(user)
                    .statementImport(statementImport)
                    .transactionDate(t.getDate())
                    .description(t.getDescription())
                    .amount(t.getAmount())
                    .category(category)
                    .hash(hash)
                    .build();

            transactionRepository.save(transaction);
            saved++;
        }

        return new ImportSummaryResponse(statementImport.getId(), statementImport.getFilename(), saved);
    }

    // Percorre as regras por prioridade e devolve a primeira categoria cuja
    // keyword aparece na descrição. Sem correspondência, usa o fallback.
    private Category resolveCategory(String description, List<CategorizationRule> rules, Category fallback) {
        String lowerDescription = description.toLowerCase();
        for (CategorizationRule rule : rules) {
            if (lowerDescription.contains(rule.getKeyword().toLowerCase())) {
                return rule.getCategory();
            }
        }
        return fallback;
    }

    // "Outros" é o fallback normal, mas não é protegida, o utilizador pode
    // apagá-la. Se isso acontecer, cai para "Sem Categoria", que é garantida.
    private Category resolveFallbackCategory(User user) {
        return categoryRepository.findByUserAndName(user, "Outros")
                .orElseGet(() -> categoryRepository.findByUserAndIsDefaultTrue(user)
                        .orElseThrow(() -> new IllegalStateException(
                                "Nenhuma categoria fallback encontrada para o utilizador")));
    }

    private String computeHash(User user, LocalDate date, String description, BigDecimal amount) {
        String raw = user.getId() + "|" + date + "|" + description + "|" + amount.stripTrailingZeros().toPlainString();
        return HashUtil.sha256Base64Url(raw);
    }
}
