package com.miguel.financemanager.controller;

import com.miguel.financemanager.dto.TransactionResponse;
import com.miguel.financemanager.dto.UpdateTransactionCategoryRequest;
import com.miguel.financemanager.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    // GET /api/transactions?month=2026-09 — omitido, usa o mês atual.
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> list(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {

        YearMonth target = month != null ? month : YearMonth.now();
        return ResponseEntity.ok(transactionService.listTransactions(target));
    }

    @PutMapping("/{id}/category")
    public ResponseEntity<TransactionResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTransactionCategoryRequest request) {
        return ResponseEntity.ok(transactionService.updateCategory(id, request));
    }
}
