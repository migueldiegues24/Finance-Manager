package com.miguel.financemanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTransactionCategoryRequest {

    @NotNull
    private Long categoryId;
}
