package com.miguel.financemanager.service.util;

import java.util.Locale;
import java.util.regex.Pattern;

// Validação da cor de uma categoria: só #RRGGBB, guardada em maiúsculas.
// null significa "cor automática". A BD tem um CHECK equivalente (V5).
public final class CategoryColors {

    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");

    private CategoryColors() {
    }

    // matches() exige que o texto inteiro corresponda (sem espaços, sem
    // quebras de linha nem texto a mais).
    public static String normalize(String color) {
        if (color == null) {
            return null;
        }
        if (!HEX.matcher(color).matches()) {
            throw new IllegalArgumentException("Cor inválida. Usa #RRGGBB.");
        }
        return color.toUpperCase(Locale.ROOT);
    }
}
