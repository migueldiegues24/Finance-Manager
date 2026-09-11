package com.miguel.financemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class CategoryTotalResponse {
    private Long categoryId;
    private String categoryName;
    // Valor positivo: quanto foi gasto nesta categoria, no mês em questão.
    private BigDecimal total;
}
