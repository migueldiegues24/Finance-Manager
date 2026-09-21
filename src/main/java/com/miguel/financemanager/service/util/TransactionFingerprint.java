package com.miguel.financemanager.service.util;

import java.math.BigDecimal;
import java.time.LocalDate;

// Fingerprint de deduplicação, usada tanto no parse (para marcar
// duplicados) como no confirm (para os recusar). Tem de ser a mesma
// função nos dois lados, senão o que o parse marca e o que o confirm
// grava deixam de bater certo.
public final class TransactionFingerprint {

    private TransactionFingerprint() {
    }

    // Com saldo (ex.: CGD): user | data mov | data-valor | descrição | montante | saldo.
    // Sem saldo (CSV genérico): user | data | descrição | montante, a fórmula
    // original, para os movimentos já importados continuarem a ser detetados.
    //
    // occurrence distingue movimentos idênticos no mesmo ficheiro (ex.: dois
    // cafés iguais no mesmo dia): 0 mantém a fórmula acima, 1, 2, ... são
    // acrescentados no fim.
    public static String compute(Long userId, LocalDate date, LocalDate movementDate, String description,
                                 BigDecimal amount, BigDecimal balanceAfter, int occurrence) {
        StringBuilder raw = new StringBuilder().append(userId).append('|');
        if (balanceAfter != null) {
            raw.append(movementDate).append('|')
                    .append(date).append('|')
                    .append(description).append('|')
                    .append(plain(amount)).append('|')
                    .append(plain(balanceAfter));
        } else {
            raw.append(date).append('|')
                    .append(description).append('|')
                    .append(plain(amount));
        }
        if (occurrence > 0) {
            raw.append("|#").append(occurrence);
        }
        return HashUtil.sha256Base64Url(raw.toString());
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
