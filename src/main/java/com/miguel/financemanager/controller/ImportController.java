package com.miguel.financemanager.controller;

import com.miguel.financemanager.dto.ConfirmImportRequest;
import com.miguel.financemanager.dto.ImportSummaryResponse;
import com.miguel.financemanager.dto.ParseImportResponse;
import com.miguel.financemanager.service.ImportService;
import com.miguel.financemanager.service.parsing.Bank;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping(value = "/parse", consumes = "multipart/form-data")
    public ResponseEntity<ParseImportResponse> parse(@RequestParam("file") MultipartFile file,
                                                     @RequestParam(value = "bank", required = false) String bank) {
        return ResponseEntity.ok(importService.parseStatement(file, Bank.fromParam(bank)));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ImportSummaryResponse> confirm(@Valid @RequestBody ConfirmImportRequest request) {
        return ResponseEntity.ok(importService.confirmImport(request));
    }
}
