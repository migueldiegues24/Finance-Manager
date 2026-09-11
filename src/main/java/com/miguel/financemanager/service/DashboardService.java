package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.CategoryTotalResponse;
import com.miguel.financemanager.dto.DashboardSummaryResponse;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    public DashboardSummaryResponse getMonthlySummary(YearMonth yearMonth) {
        User user = currentUserService.getCurrentUser();

        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.plusMonths(1).atDay(1);

        List<Object[]> rows = transactionRepository.sumExpensesByCategory(user, start, end);

        List<CategoryTotalResponse> totals = rows.stream()
                .map(row -> {
                    Long categoryId = (Long) row[0];
                    String categoryName = (String) row[1];
                    BigDecimal total = ((BigDecimal) row[2]).abs();
                    return new CategoryTotalResponse(categoryId, categoryName, total);
                })
                .sorted(Comparator.comparing(CategoryTotalResponse::getTotal).reversed())
                .toList();

        BigDecimal overallTotal = totals.stream()
                .map(CategoryTotalResponse::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardSummaryResponse(yearMonth.toString(), totals, overallTotal);
    }
}
