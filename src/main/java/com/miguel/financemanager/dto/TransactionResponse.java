package com.miguel.financemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class TransactionResponse {
    private Long id;
    private LocalDate date;
    private String description;
    private BigDecimal amount;
    private Long categoryId;
    private String categoryName;
}
