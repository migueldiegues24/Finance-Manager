package com.miguel.financemanager.dto;

import java.time.Instant;

// Uma sessão ativa (refresh token não revogado e não expirado). Nunca leva o
// token nem o hash. createdAt é a emissão deste refresh token, ou seja, a
// última renovação da sessão (a rotação cria um token novo a cada renovação).
public record SessionResponse(long id, Instant createdAt, Instant expiresAt, Mode mode, boolean current) {

    public enum Mode {
        REMEMBERED, SHORT
    }
}
