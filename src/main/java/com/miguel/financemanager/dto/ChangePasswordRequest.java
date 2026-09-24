package com.miguel.financemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank
    private String currentPassword;

    @NotBlank
    // Mesmos limites do registo (ver RegisterRequest e PasswordPolicy).
    @Size(min = 12, max = 64, message = "A password deve ter entre 12 e 64 caracteres")
    private String newPassword;
}
