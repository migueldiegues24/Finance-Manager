package com.miguel.financemanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Confirmação para apagar a conta: a password atual.
@Getter
@Setter
public class DeleteAccountRequest {

    @NotBlank
    private String password;
}
