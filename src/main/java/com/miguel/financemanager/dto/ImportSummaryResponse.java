package com.miguel.financemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImportSummaryResponse {
    private Long importId;
    private String filename;
    // Total gravado, incluindo importedAsDuplicate.
    private int transactionsSaved;
    // Movimentos que já existiam (mesmo fingerprint) e não foram gravados de novo.
    private int duplicatesSkipped;
    // Movimentos que já existiam e foram gravados outra vez a pedido (allowDuplicate).
    private int importedAsDuplicate;
}
