package com.miguel.financemanager.exception;

// Conteúdo acima de um limite da app (ex.: movimentos por extrato): 413.
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
