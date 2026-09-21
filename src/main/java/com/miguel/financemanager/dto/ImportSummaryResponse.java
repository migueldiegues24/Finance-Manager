package com.miguel.financemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImportSummaryResponse {
    private Long importId;
    private String filename;
    private int transactionsSaved;
    // Movimentos que já existiam (mesmo fingerprint) e não foram gravados de novo.
    private int duplicatesSkipped;
}
