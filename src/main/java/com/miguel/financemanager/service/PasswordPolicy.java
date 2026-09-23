package com.miguel.financemanager.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

// Regras de password no registo, além do tamanho (12 a 64 caracteres, no
// RegisterRequest). Sem regras de composição (maiúsculas, símbolos...), como
// recomenda o NIST SP 800-63B: o que protege é o comprimento e evitar as
// passwords que aparecem primeiro nas listas de ataque.
//
// A lista é pequena e embutida (sem serviço externo). Com mínimo de 12, as
// clássicas curtas ("123456", "password") já caem pelo tamanho, por isso só
// tem as comuns com 12 ou mais caracteres. Compara sem distinguir
// maiúsculas/minúsculas.
public final class PasswordPolicy {

    // Limite do BCrypt: o que passar de 72 bytes é recusado pelo encoder.
    static final int MAX_BYTES = 72;

    private static final Set<String> COMMON = Set.of(
            // Sequências e teclado
            "123456789012", "1234567890123", "12345678901234", "123456789123", "1234567891011",
            "123123123123", "123412341234", "121212121212", "123456123456", "123456654321",
            "987654321098", "098765432109", "147258369147", "159753159753", "012345678901",
            "abcdefghijkl", "abcdefghijklm", "abcdefghijklmnop", "abc123456789", "abcd12345678",
            "abc123abc123", "qwertyuiop12", "qwertyuiop123", "qwertyuiopas", "qwertyuiopasdf",
            "qwerty123456", "qwertyqwerty", "qwerty123qwerty", "asdfghjkl123", "asdfghjklqwe",
            "asdfasdfasdf", "zxcvbnm12345", "zxcvbnmasdfg", "1qaz2wsx3edc", "1qaz2wsx3edc4rfv",
            "1q2w3e4r5t6y", "1q2w3e4r5t6y7u", "q1w2e3r4t5y6", "qazwsxedcrfv", "zaq12wsxcde3",
            "zaq1zaq1zaq1", "!qaz@wsx#edc", "aaaaaa111111", "a1b2c3d4e5f6",
            // Palavras comuns com números
            "password1234", "password12345", "password123456", "password1234567", "passwordpassword",
            "password2024", "password2025", "password2026", "password!123", "passw0rd1234",
            "p@ssw0rd1234", "p@ssword1234", "mypassword123", "iloveyou1234", "iloveyouiloveyou",
            "letmein12345", "welcome12345", "welcome2024!", "changeme1234", "administrator",
            "administrator1", "adminadmin123", "admin1234567", "monkey123456", "dragon123456",
            "football1234", "baseball1234", "sunshine1234", "princess1234", "superman1234",
            "batman123456", "starwars1234", "pokemon12345", "trustno1trustno1", "whatever1234",
            // Português
            "palavrapasse", "palavra-passe", "palavrapasse1", "palavrapasse123", "minhapalavrapasse",
            "minhapassword", "minhasenha123", "senha123456789", "senhasenha123", "administrador",
            "benfica12345", "benfica123456", "sporting1906", "sporting12345", "fcporto12345",
            "portugal1234", "portugal12345", "portugal2024", "portugal2025", "portugal2026",
            "lisboa123456", "amoteamoteamo", "olaola123456", "euamoaminhamae",
            // Esta app
            "financemanager", "finance-manager", "financemanager1", "financemanager123"
    );

    private PasswordPolicy() {
    }

    // Lança IllegalArgumentException (400 com a mensagem) se a password não servir.
    public static void validate(String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("A password é demasiado longa. Usa uma mais curta.");
        }

        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower) || lower.chars().distinct().count() == 1) {
            throw new IllegalArgumentException("Esta password é demasiado comum ou fácil de adivinhar. Escolhe outra.");
        }

        String localPart = emailLocalPart(email);
        if (localPart.length() >= 4 && lower.contains(localPart)) {
            throw new IllegalArgumentException("A password não pode conter o teu email.");
        }
    }

    private static String emailLocalPart(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        return (at < 0 ? email : email.substring(0, at)).toLowerCase(Locale.ROOT);
    }
}
