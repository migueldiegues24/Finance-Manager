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
import com.miguel.financemanager.service.parsing.StatementParserRegistry;
import com.miguel.financemanager.service.util.DescriptionNormalizer;
import com.miguel.financemanager.service.util.TransactionFingerprint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final StatementParserRegistry parserRegistry;
    private final TransactionRepository transactionRepository;
    private final StatementImportRepository statementImportRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationRuleRepository ruleRepository;
    private final CurrentUserService currentUserService;

    // Não persiste nada. O frontend mostra esta lista numa página de
    // revisão, onde o utilizador remove o que não quer antes de confirmar.
    public ParseImportResponse parseStatement(MultipartFile file, Bank bank) {
        User user = currentUserService.getCurrentUser();

        List<ParsedTransaction> parsed;
        try {
            parsed = parserRegistry.forBank(bank).parse(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível ler o ficheiro: " + e.getMessage());
        }

        // Movimentos idênticos no mesmo ficheiro recebem ocorrências 0, 1, 2...
        // pela ordem em que aparecem, para não colidirem entre si.
        Map<String, Integer> seen = new HashMap<>();
        List<ParsedTransactionResponse> response = new ArrayList<>();
        for (ParsedTransaction t : parsed) {
            String baseHash = fingerprint(user, t, 0);
            int occurrence = seen.merge(baseHash, 1, Integer::sum) - 1;
            String hash = occurrence == 0 ? baseHash : fingerprint(user, t, occurrence);
            boolean duplicate = transactionRepository.existsByUserAndHash(user, hash);
            response.add(new ParsedTransactionResponse(t.date(), t.movementDate(), t.description(), t.amount(),
                    t.balanceAfter(), occurrence, hash, duplicate));
        }

        return new ParseImportResponse(file.getOriginalFilename(), response);
    }

    @Transactional
    public ImportSummaryResponse confirmImport(ConfirmImportRequest request) {
        User user = currentUserService.getCurrentUser();

        StatementImport statementImport = StatementImport.builder()
                .user(user)
                .filename(request.getFilename())
                .bank(request.getBank() != null ? request.getBank() : Bank.GENERIC)
                .build();
        statementImportRepository.save(statementImport);

        List<CategorizationRule> rules = ruleRepository.findByUserOrderByPriorityAsc(user);
        Category fallback = resolveFallbackCategory(user);

        int saved = 0;
        int skipped = 0;
        Set<String> hashesInRequest = new HashSet<>();
        for (ConfirmTransactionRequest t : request.getTransactions()) {
            String hash = TransactionFingerprint.compute(user.getId(), t.getDate(), t.getMovementDate(),
                    t.getDescription(), t.getAmount(), t.getBalanceAfter(), t.getOccurrence());

            // Movimentos idênticos legítimos chegam com ocorrências diferentes;
            // o mesmo hash duas vezes só acontece se o pedido estiver mal formado.
            if (!hashesInRequest.add(hash)) {
                throw new IllegalArgumentException("Movimento repetido no pedido: " + t.getDate() + " "
                        + t.getDescription() + " " + t.getAmount() + " (ocorrência " + t.getOccurrence() + ")");
            }
            if (transactionRepository.existsByUserAndHash(user, hash)) {
                skipped++;
                continue;
            }

            Category category = resolveCategory(t.getDescription(), rules, fallback);

            Transaction transaction = Transaction.builder()
                    .user(user)
                    .statementImport(statementImport)
                    .transactionDate(t.getDate())
                    .movementDate(t.getMovementDate())
                    .description(t.getDescription())
                    .amount(t.getAmount())
                    .balanceAfter(t.getBalanceAfter())
                    .category(category)
                    .hash(hash)
                    .build();

            transactionRepository.save(transaction);
            saved++;
        }

        return new ImportSummaryResponse(statementImport.getId(), statementImport.getFilename(), saved, skipped);
    }

    // Percorre as regras por prioridade e devolve a primeira categoria cuja
    // keyword está contida na descrição, comparando ambas normalizadas
    // (maiúsculas, sem acentos, espaços colapsados). Sem correspondência, usa o fallback.
    private Category resolveCategory(String description, List<CategorizationRule> rules, Category fallback) {
        String normalizedDescription = DescriptionNormalizer.normalize(description);
        for (CategorizationRule rule : rules) {
            if (normalizedDescription.contains(DescriptionNormalizer.normalize(rule.getKeyword()))) {
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

    private String fingerprint(User user, ParsedTransaction t, int occurrence) {
        return TransactionFingerprint.compute(user.getId(), t.date(), t.movementDate(), t.description(),
                t.amount(), t.balanceAfter(), occurrence);
    }
}
