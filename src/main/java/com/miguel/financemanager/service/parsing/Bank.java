package com.miguel.financemanager.service.parsing;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

// Banco de origem de um extrato; decide qual StatementParser é usado.
public enum Bank {
    GENERIC,
    CGD;

    // Converte o parâmetro do pedido (ex.: "cgd") com uma mensagem de erro
    // legível, em vez do erro genérico de conversão do Spring.
    public static Bank fromParam(String value) {
        if (value == null || value.isBlank()) {
            return GENERIC;
        }
        try {
            return Bank.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Banco desconhecido: '" + value + "'. Valores aceites: " + valid);
        }
    }
}
