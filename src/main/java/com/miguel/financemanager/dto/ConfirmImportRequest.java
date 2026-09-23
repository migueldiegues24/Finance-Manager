package com.miguel.financemanager.dto;

import com.miguel.financemanager.service.parsing.Bank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ConfirmImportRequest {

    @NotBlank
    @Size(max = 255)
    private String filename;

    // Opcional; sem valor assume-se o CSV genérico.
    private Bank bank;

    // O máximo (StatementParser.MAX_TRANSACTIONS) é verificado no
    // ImportService, para responder 413 como no parse.
    @NotEmpty
    @Valid
    private List<ConfirmTransactionRequest> transactions;
}
