package com.miguel.financemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    // Opcional: #RRGGBB, ou null/ausente para a cor automática. O PUT
    // substitui a categoria inteira, por isso uma cor ausente limpa a cor.
    private String color;
}
