package com.miguel.financemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ParsedTransactionResponse {
    private LocalDate date;
    private LocalDate movementDate;
    private String description;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    // Índice entre movimentos idênticos do mesmo ficheiro; o frontend
    // devolve-o no confirm para o hash ser recalculado igual.
    private int occurrence;
    private String hash;
    private boolean duplicate;
}
