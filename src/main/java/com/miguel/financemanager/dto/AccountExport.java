package com.miguel.financemanager.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Tudo o que é do utilizador, para GET /api/account/export. Fica de fora o
// que não é dado pessoal útil nem deve sair: o hash da password, os refresh
// tokens (e sessões) e a fingerprint de deduplicação das transações.
// As referências entre listas são pelos ids (categoryId, importId).
public record AccountExport(
        Instant exportedAt,
        Profile profile,
        List<CategoryEntry> categories,
        List<RuleEntry> rules,
        List<ImportEntry> imports,
        List<TransactionEntry> transactions) {

    public record Profile(String email, LocalDateTime createdAt) {
    }

    // defaultCategory = a categoria protegida "Sem Categoria" (como no CategoryResponse).
    public record CategoryEntry(long id, String name, String color, boolean defaultCategory,
                                LocalDateTime createdAt) {
    }

    public record RuleEntry(long id, String keyword, long categoryId, String categoryName, int priority) {
    }

    public record ImportEntry(long id, String filename, String bank, LocalDateTime importedAt) {
    }

    public record TransactionEntry(long id, LocalDate date, LocalDate movementDate, String description,
                                   BigDecimal amount, BigDecimal balanceAfter, long categoryId,
                                   String categoryName, long importId, LocalDateTime createdAt) {
    }
}
