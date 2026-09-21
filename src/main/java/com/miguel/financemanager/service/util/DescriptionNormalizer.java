package com.miguel.financemanager.service.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

// Forma canónica usada para comparar descrições com as keywords das regras:
// maiúsculas, sem acentos e com espaços colapsados. Não remove prefixos
// ("TFI", "TRF MBWAY", ...), para que também possam ser usados em regras.
public final class DescriptionNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

    private DescriptionNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return WHITESPACE.matcher(withoutAccents).replaceAll(" ").trim().toUpperCase(Locale.ROOT);
    }
}
