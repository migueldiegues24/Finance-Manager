package com.miguel.financemanager.exception;

// Não foi possível determinar a próxima ocorrência para gravar um duplicado
// pedido (os hashes guardados não correspondem aos campos). Nunca se tenta
// adivinhar um índice; devolvido como 409 com a explicação.
public class DuplicateResolutionException extends RuntimeException {

    public DuplicateResolutionException(String message) {
        super(message);
    }
}
