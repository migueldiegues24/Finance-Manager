package com.miguel.financemanager.service.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionFingerprintTest {

    private static final LocalDate VALUE_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDate MOVEMENT_DATE = LocalDate.of(2026, 8, 2);

    @Test
    void withoutBalanceAndOccurrenceZero_matchesOriginalFormula() {
        // Fórmula usada antes desta alteração; os hashes já gravados têm de continuar iguais.
        String legacy = HashUtil.sha256Base64Url("1|2026-09-01|Uber viagem|-8.5");

        assertThat(TransactionFingerprint.compute(1L, LocalDate.of(2026, 9, 1), null, "Uber viagem",
                new BigDecimal("-8.50"), null, 0)).isEqualTo(legacy);
    }

    @Test
    void occurrenceDistinguishesIdenticalMovements() {
        String first = TransactionFingerprint.compute(1L, VALUE_DATE, null, "Cafe", new BigDecimal("-1.20"), null, 0);
        String second = TransactionFingerprint.compute(1L, VALUE_DATE, null, "Cafe", new BigDecimal("-1.20"), null, 1);

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void balanceDistinguishesMovementsThatOtherwiseCollide() {
        String a = TransactionFingerprint.compute(1L, VALUE_DATE, MOVEMENT_DATE, "COMPRAS C.DEB CAFE",
                new BigDecimal("-1.20"), new BigDecimal("1048.80"), 0);
        String b = TransactionFingerprint.compute(1L, VALUE_DATE, MOVEMENT_DATE, "COMPRAS C.DEB CAFE",
                new BigDecimal("-1.20"), new BigDecimal("1047.60"), 0);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void movementDateIsPartOfFingerprintWhenBalanceIsPresent() {
        String a = TransactionFingerprint.compute(1L, VALUE_DATE, MOVEMENT_DATE, "X",
                new BigDecimal("-1.20"), new BigDecimal("10"), 0);
        String b = TransactionFingerprint.compute(1L, VALUE_DATE, VALUE_DATE, "X",
                new BigDecimal("-1.20"), new BigDecimal("10"), 0);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void trailingZerosDoNotChangeFingerprint() {
        assertThat(TransactionFingerprint.compute(1L, VALUE_DATE, MOVEMENT_DATE, "X",
                new BigDecimal("-1.2"), new BigDecimal("10"), 0))
                .isEqualTo(TransactionFingerprint.compute(1L, VALUE_DATE, MOVEMENT_DATE, "X",
                        new BigDecimal("-1.20"), new BigDecimal("10.00"), 0));
    }
}
