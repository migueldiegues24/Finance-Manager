package com.miguel.financemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ParseImportResponse {
    private String filename;
    private List<ParsedTransactionResponse> transactions;
}
