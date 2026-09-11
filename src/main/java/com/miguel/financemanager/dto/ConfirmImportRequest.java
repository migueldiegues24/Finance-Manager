package com.miguel.financemanager.dto;

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

    @NotEmpty
    @Valid
    private List<ConfirmTransactionRequest> transactions;
}
