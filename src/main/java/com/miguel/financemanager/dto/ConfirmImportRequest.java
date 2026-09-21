package com.miguel.financemanager.dto;

import com.miguel.financemanager.service.parsing.Bank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ConfirmImportRequest {

    @NotBlank
    private String filename;

    // Opcional; sem valor assume-se o CSV genérico.
    private Bank bank;

    @NotEmpty
    @Valid
    private List<ConfirmTransactionRequest> transactions;
}
