package com.miguel.financemanager.exception;

// Recurso do path (/{id}) que não existe ou que é de outro utilizador: 404
// nos dois casos, com a mesma mensagem. É deliberado não usar 403 para os
// recursos alheios: um 403 confirmaria que o id existe (e, com ids
// sequenciais, deixava contar os registos das outras contas).
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
