package com.miguel.financemanager.exception;

// O registo público está desligado (REGISTRATION_ENABLED=false). Devolvido como 403.
public class RegistrationDisabledException extends RuntimeException {

    public RegistrationDisabledException() {
        super("O registo de novas contas está desativado.");
    }
}
