package com.miguel.financemanager.exception;

// Outra importação gravou os mesmos movimentos ao mesmo tempo e a nova
// tentativa voltou a colidir. Devolvido como 409.
public class ImportConflictException extends RuntimeException {

    public ImportConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
