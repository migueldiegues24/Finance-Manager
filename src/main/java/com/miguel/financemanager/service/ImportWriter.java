package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.ConfirmImportRequest;
import com.miguel.financemanager.dto.ConfirmTransactionRequest;
import com.miguel.financemanager.dto.ImportSummaryResponse;
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
import com.miguel.financemanager.service.util.DescriptionNormalizer;
import com.miguel.financemanager.service.util.TransactionFingerprint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Grava uma importação confirmada numa única transação. Está separado do
// ImportService para que uma violação do índice único (dois confirms em
// simultâneo) reverta a transação inteira e o ImportService possa repetir
// a escrita numa transação nova: numa transação Postgres a violação
// invalida-a, por isso não pode ser tratada lá dentro.
@Component
@RequiredArgsConstructor
public class ImportWriter {

    private final TransactionRepository transactionRepository;
    private final StatementImportRepository statementImportRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationRuleRepository ruleRepository;

    @Transactional
    public ImportSummaryResponse write(User user, ConfirmImportRequest request) {
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
}
