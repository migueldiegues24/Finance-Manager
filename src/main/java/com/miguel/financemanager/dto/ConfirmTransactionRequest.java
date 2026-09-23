package com.miguel.financemanager.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 500)
    private String description;

    // Máximo 2 casas decimais, como a coluna: um valor arredondado ao gravar
    // deixaria de corresponder ao hash calculado.
    @NotNull
    @Digits(integer = 10, fraction = 2)
    private BigDecimal amount;

    private LocalDate movementDate;

    @Digits(integer = 10, fraction = 2)
    private BigDecimal balanceAfter;

    @Min(0)
    private int occurrence;

    // O utilizador marcou de propósito um movimento que já existe. Só é
    // respeitado se o hash já existir; nesse caso grava-se com a próxima
    // ocorrência livre (máximo existente + 1).
    private boolean allowDuplicate;
}
