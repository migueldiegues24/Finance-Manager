package com.miguel.financemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ParsedTransactionResponse {
    private LocalDate date;
    private String description;
    private BigDecimal amount;
    private String hash;
    private boolean duplicate;
}
