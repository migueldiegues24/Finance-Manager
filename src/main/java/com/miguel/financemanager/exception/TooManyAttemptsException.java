package com.miguel.financemanager.exception;

import lombok.Getter;

// Limite de tentativas atingido: 429 com Retry-After. A mensagem é sempre a
// mesma, exista ou não o email, para não dar pistas sobre as contas.
@Getter
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super(message(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static String message(long seconds) {
        if (seconds < 60) {
            return "Demasiadas tentativas. Tenta de novo daqui a " + seconds + " segundos.";
        }
        long minutes = (seconds + 59) / 60;
        return "Demasiadas tentativas. Tenta de novo daqui a " + minutes
                + (minutes == 1 ? " minuto." : " minutos.");
    }
}
