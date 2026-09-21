package com.miguel.financemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class DashboardSummaryResponse {
    // Formato "yyyy-MM", ex.: "2026-09"
    private String month;
    private List<CategoryTotalResponse> totals;
    // Total das despesas por categoria (positivo). Igual a totalExpenses;
    // mantido por compatibilidade.
    private BigDecimal overallTotal;
    // Receitas do mês (positivo).
    private BigDecimal totalIncome;
    // Despesas do mês (positivo).
    private BigDecimal totalExpenses;
    // Balanço do mês: totalIncome - totalExpenses (pode ser negativo).
    private BigDecimal net;
}
