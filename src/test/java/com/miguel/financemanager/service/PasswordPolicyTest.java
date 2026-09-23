package com.miguel.financemanager.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void acceptsReasonablePassword() {
        assertThatCode(() -> PasswordPolicy.validate("ana@teste.com", "cavalo-bateria-agrafo"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"password1234", "PASSWORD1234", "PassWord1234", "Palavrapasse", "QWERTY123456",
            "1q2w3e4r5t6y", "Portugal2026"})
    void rejectsCommonPasswords_caseInsensitive(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate("ana@teste.com", password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demasiado comum");
    }

    @Test
    void rejectsSingleRepeatedCharacter() {
        assertThatThrownBy(() -> PasswordPolicy.validate("ana@teste.com", "zzzzzzzzzzzzzz"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demasiado comum");
    }

    @Test
    void rejectsPasswordContainingEmailLocalPart() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Miguel.Silva@teste.com", "xx-miguel.silva-99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void shortLocalPart_isNotChecked() {
        assertThatCode(() -> PasswordPolicy.validate("ana@teste.com", "banana-com-canela"))
                .doesNotThrowAnyException();
    }

    // 64 caracteres podem passar dos 72 bytes do BCrypt (acentos, emojis).
    @Test
    void rejectsMoreThan72Bytes() {
        String twentyFiveEmojis = "😀".repeat(25); // 50 caracteres, 100 bytes

        assertThatThrownBy(() -> PasswordPolicy.validate("ana@teste.com", twentyFiveEmojis))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demasiado longa");
        assertThatCode(() -> PasswordPolicy.validate("ana@teste.com", "x".repeat(40) + "y".repeat(32)))
                .doesNotThrowAnyException();
    }
}
