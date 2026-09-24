package com.miguel.financemanager.security;

import com.miguel.financemanager.exception.TooManyAttemptsException;
import com.miguel.financemanager.security.AttemptLimiter.Policy;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

// Limites de tentativas dos endpoints de /api/account, por utilizador (são
// todos autenticados, por isso a chave é o id da conta e não o IP). Em
// memória, como o AuthRateLimiter.
//
//  - password atual errada: 5 falhas em 15 min -> bloqueio de 30 s a duplicar
//    até 15 min; uma password certa repõe a chave. A chave é partilhada entre
//    mudar a password e apagar a conta, para nenhum dos dois servir para
//    adivinhar a password enquanto o outro está bloqueado.
//
//  - exportação: 10 por hora (lê todos os dados da conta de uma vez).
//
// Os endpoints de sessões não têm limite: são baratos e não verificam passwords.
@Component
public class AccountRateLimiter {

    private static final Duration BASE_LOCK = Duration.ofSeconds(30);
    private static final Duration MAX_LOCK = Duration.ofMinutes(15);
    private static final Duration BACKOFF_MEMORY = Duration.ofHours(24);
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final AttemptLimiter passwordByUser = new AttemptLimiter(
            Policy.withBackoff(5, FAILURE_WINDOW, BASE_LOCK, MAX_LOCK, BACKOFF_MEMORY),
            AuthRateLimiter.MAX_ENTRIES_PER_LIMIT);
    private final AttemptLimiter exportByUser = new AttemptLimiter(
            Policy.fixedWindow(10, Duration.ofHours(1)), AuthRateLimiter.MAX_ENTRIES_PER_LIMIT);

    public AccountRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkPassword(long userId) {
        throwIfBlocked(passwordByUser.retryAfterSeconds(key(userId), clock.instant()));
    }

    public void passwordFailed(long userId) {
        passwordByUser.recordAttempt(key(userId), clock.instant());
    }

    public void passwordSucceeded(long userId) {
        passwordByUser.reset(key(userId));
    }

    // Conta o pedido se não estiver bloqueado.
    public void exportRequested(long userId) {
        throwIfBlocked(exportByUser.retryAfterSeconds(key(userId), clock.instant()));
        exportByUser.recordAttempt(key(userId), clock.instant());
    }

    // Só para os testes: o contexto Spring (e este bean) é partilhado entre eles.
    public void reset() {
        passwordByUser.clear();
        exportByUser.clear();
    }

    private static String key(long userId) {
        return Long.toString(userId);
    }

    private static void throwIfBlocked(long retryAfterSeconds) {
        if (retryAfterSeconds > 0) {
            throw new TooManyAttemptsException(retryAfterSeconds);
        }
    }
}
