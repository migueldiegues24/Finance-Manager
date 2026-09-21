package com.miguel.financemanager.controller;

import com.miguel.financemanager.dto.DashboardSummaryResponse;
import com.miguel.financemanager.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    // GET /api/dashboard/summary?month=2026-09 — omitido, usa o mês atual.
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {

        YearMonth target = month != null ? month : YearMonth.now();
        return ResponseEntity.ok(dashboardService.getMonthlySummary(target));
    }

    // GET /api/dashboard/months — ["2026-08", "2026-09", ...], só meses com transações.
    @GetMapping("/months")
    public ResponseEntity<List<String>> months() {
        return ResponseEntity.ok(dashboardService.getMonthsWithTransactions());
    }
}
