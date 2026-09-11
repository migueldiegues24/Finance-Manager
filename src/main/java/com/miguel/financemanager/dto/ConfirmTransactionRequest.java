package com.miguel.financemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ConfirmTransactionRequest {

    @NotNull
    private LocalDate date;

    @NotBlank
    private String description;

    @NotNull
    private BigDecimal amount;
}
