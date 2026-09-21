package com.miguel.financemanager.service.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryColorsTest {

    @Test
    void validColourIsStoredInUppercase() {
        assertThat(CategoryColors.normalize("#1f5e6b")).isEqualTo("#1F5E6B");
        assertThat(CategoryColors.normalize("#ABCDEF")).isEqualTo("#ABCDEF");
    }

    @Test
    void nullMeansAutomaticColour() {
        assertThat(CategoryColors.normalize(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "red", "#12", "#FFF", "#GGGGGG", "url(x)", "#ff0000;x", " #FF0000",
            "#FF0000 ", "#FF0000\n", "FF0000", "#FF00000", "rgb(0,0,0)"})
    void anythingElseIsRejectedWithShortMessage(String value) {
        assertThatThrownBy(() -> CategoryColors.normalize(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cor inválida. Usa #RRGGBB.");
    }
}
