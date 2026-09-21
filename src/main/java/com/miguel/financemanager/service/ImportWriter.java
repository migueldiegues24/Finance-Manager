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
import com.miguel.financemanager.service.util.DescriptionNormalizer;
import com.miguel.financemanager.service.util.TransactionFingerprint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    // Folga para buracos entre ocorrências ao procurar o máximo existente.
    static final int MAX_OCCURRENCE_GAP = 1000;

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
        List<ConfirmTransactionRequest> forcedDuplicates = new ArrayList<>();

        // 1.ª passagem: movimentos novos. Os duplicados pedidos ficam para o
        // fim, para a ocorrência que lhes é atribuída não roubar o hash de um
        // movimento legítimo que venha mais à frente no mesmo pedido.
        for (ConfirmTransactionRequest t : request.getTransactions()) {
            String hash = fingerprint(user, t, t.getOccurrence());

            // Movimentos idênticos legítimos chegam com ocorrências diferentes;
            // o mesmo hash duas vezes só acontece se o pedido estiver mal formado.
            if (!hashesInRequest.add(hash)) {
                throw new IllegalArgumentException("Movimento repetido no pedido: " + t.getDate() + " "
                        + t.getDescription() + " " + t.getAmount() + " (ocorrência " + t.getOccurrence() + ")");
            }
            if (transactionRepository.existsByUserAndHash(user, hash)) {
                if (t.isAllowDuplicate()) {
                    forcedDuplicates.add(t);
                } else {
                    skipped++;
                }
                continue;
            }

            // allowDuplicate numa linha que ainda não existe é ignorado.
            save(user, statementImport, t, hash, rules, fallback);
            saved++;
        }

        // 2.ª passagem: duplicados pedidos, com ocorrência = máximo existente + 1.
        for (ConfirmTransactionRequest t : forcedDuplicates) {
            int occurrence = maxExistingOccurrence(user, t) + 1;
            save(user, statementImport, t, fingerprint(user, t, occurrence), rules, fallback);
            saved++;
        }

        return new ImportSummaryResponse(statementImport.getId(), statementImport.getFilename(), saved, skipped,
                forcedDuplicates.size());
    }

    private void save(User user, StatementImport statementImport, ConfirmTransactionRequest t, String hash,
                      List<CategorizationRule> rules, Category fallback) {
        Transaction transaction = Transaction.builder()
                .user(user)
                .statementImport(statementImport)
                .transactionDate(t.getDate())
                .movementDate(t.getMovementDate())
                .description(t.getDescription())
                .amount(t.getAmount())
                .balanceAfter(t.getBalanceAfter())
                .category(resolveCategory(t.getDescription(), rules, fallback))
                .hash(hash)
                .build();
        transactionRepository.save(transaction);
    }

    // A ocorrência não é guardada, só o hash. Os campos da fingerprint estão
    // guardados, por isso: buscar as N linhas do utilizador com os mesmos
    // campos e recalcular fingerprint(k) para k = 0, 1, 2... até reconhecer os
    // N hashes. O maior k reconhecido é o máximo, mesmo com buracos (ex.: 0 e 2).
    // Se os N hashes não forem reconhecidos, falha em vez de adivinhar.
    int maxExistingOccurrence(User user, ConfirmTransactionRequest t) {
        Set<String> existing = new HashSet<>(transactionRepository.findHashesWithSameFields(
                user, t.getDate(), t.getMovementDate(), t.getDescription(), t.getAmount(), t.getBalanceAfter()));

        int limit = existing.size() + MAX_OCCURRENCE_GAP;
        int recognised = 0;
        int max = -1;
        for (int k = 0; recognised < existing.size(); k++) {
            if (k > limit) {
                throw new DuplicateResolutionException("Não foi possível gravar o duplicado de \""
                        + t.getDescription() + "\" (" + t.getDate() + ", " + t.getAmount() + "): existem "
                        + existing.size() + " movimentos iguais mas só " + recognised
                        + " correspondem à fingerprint esperada (ocorrências 0 a " + limit + ").");
            }
            if (existing.contains(fingerprint(user, t, k))) {
                recognised++;
                max = k;
            }
        }
        return max;
    }

    private static String fingerprint(User user, ConfirmTransactionRequest t, int occurrence) {
        return TransactionFingerprint.compute(user.getId(), t.getDate(), t.getMovementDate(),
                t.getDescription(), t.getAmount(), t.getBalanceAfter(), occurrence);
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
