package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.DashboardSummaryResponse;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private DashboardService dashboardService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("miguel@teste.com").passwordHash("hash").build();
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void getMonthlySummary_convertsNegativeAmountsToPositiveTotalsAndSortsDescending() {
        List<Object[]> rows = List.of(
                new Object[]{1L, "Alimentação", new BigDecimal("-45.30")},
                new Object[]{2L, "Transporte", new BigDecimal("-500.00")}
        );

        when(transactionRepository.sumExpensesByCategory(eq(user), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(rows);

        DashboardSummaryResponse response = dashboardService.getMonthlySummary(YearMonth.of(2026, 9));

        assertThat(response.getMonth()).isEqualTo("2026-09");
        assertThat(response.getTotals()).hasSize(2);
        // Transporte (500) é a maior despesa, deve vir primeiro
        assertThat(response.getTotals().get(0).getCategoryName()).isEqualTo("Transporte");
        assertThat(response.getTotals().get(0).getTotal()).isEqualByComparingTo("500.00");
        assertThat(response.getOverallTotal()).isEqualByComparingTo("545.30");
    }

    @Test
    void getMonthlySummary_usesFirstAndLastDayOfMonthAsBounds() {
        when(transactionRepository.sumExpensesByCategory(eq(user), any(), any())).thenReturn(List.of());

        dashboardService.getMonthlySummary(YearMonth.of(2026, 2));

        verify(transactionRepository).sumExpensesByCategory(
                user, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
    }

    @Test
    void getMonthlySummary_returnsZeroOverallTotalWhenNoExpenses() {
        when(transactionRepository.sumExpensesByCategory(eq(user), any(), any())).thenReturn(List.of());

        DashboardSummaryResponse response = dashboardService.getMonthlySummary(YearMonth.of(2026, 1));

        assertThat(response.getTotals()).isEmpty();
        assertThat(response.getOverallTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
