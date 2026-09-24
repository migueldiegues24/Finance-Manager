package com.miguel.financemanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    // Mínimo 12: com o BCrypt de custo 10, 8 caracteres caem depressa num ataque
    // offline se a BD fugir. Máximo 64 (o BCrypt só aceita até 72 bytes; ver
    // PasswordPolicy, que também recusa as passwords mais comuns).
    @Size(min = 12, max = 64, message = "A password deve ter entre 12 e 64 caracteres")
    private String password;
}
