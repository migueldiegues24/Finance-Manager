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
    private BigDecimal overallTotal;
}
