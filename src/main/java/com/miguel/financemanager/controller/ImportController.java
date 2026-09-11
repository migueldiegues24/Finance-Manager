package com.miguel.financemanager.controller;

import com.miguel.financemanager.dto.ConfirmImportRequest;
import com.miguel.financemanager.dto.ImportSummaryResponse;
import com.miguel.financemanager.dto.ParseImportResponse;
import com.miguel.financemanager.service.ImportService;
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
    public ResponseEntity<ParseImportResponse> parse(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(importService.parseStatement(file));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ImportSummaryResponse> confirm(@Valid @RequestBody ConfirmImportRequest request) {
        return ResponseEntity.ok(importService.confirmImport(request));
    }
}
