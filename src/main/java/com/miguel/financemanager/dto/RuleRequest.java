package com.miguel.financemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RuleRequest {

    @NotBlank
    private String keyword;

    @NotNull
    private Long categoryId;

    // Opcional: se omitida, a regra é acrescentada no fim (prioridade mais baixa,
    // avaliada por último).
    private Integer priority;
}
