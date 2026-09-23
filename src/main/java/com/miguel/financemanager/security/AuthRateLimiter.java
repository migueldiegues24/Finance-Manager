package com.miguel.financemanager.security;

import com.miguel.financemanager.exception.TooManyAttemptsException;
import com.miguel.financemanager.security.AttemptLimiter.Policy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

// Limites de tentativas dos endpoints de autenticação. Em memória: chega para
// uma só instância; com várias réplicas cada uma teria os seus contadores.
//
//  - login, por IP+email: 5 falhas em 15 min -> bloqueio de 30 s a duplicar
//    até 15 min; um login certo repõe a chave.
//  - login, só por IP: 20 falhas em 15 min, mesmo backoff; um login certo NÃO
//    a repõe (senão uma conta válida servia para limpar o contador).
//  - registo, por IP: 10 pedidos por hora (os que passam a validação).
//  - refresh, por IP: 10 falhas em 15 min, mesmo backoff.
//
// Só as falhas contam no login e no refresh: pedidos válidos repetidos nunca
// bloqueiam. O email entra na chave como hash, por isso o tamanho da chave
// não depende do que o cliente envia.
@Component
public class AuthRateLimiter {

    static final int MAX_ENTRIES_PER_LIMIT = 10_000;

    private static final Duration BASE_LOCK = Duration.ofSeconds(30);
    private static final Duration MAX_LOCK = Duration.ofMinutes(15);
    private static final Duration BACKOFF_MEMORY = Duration.ofHours(24);
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final AttemptLimiter loginByIpAndEmail = backoff(5);
    private final AttemptLimiter loginByIp = backoff(20);
    private final AttemptLimiter registerByIp =
            new AttemptLimiter(Policy.fixedWindow(10, Duration.ofHours(1)), MAX_ENTRIES_PER_LIMIT);
    private final AttemptLimiter refreshByIp = backoff(10);

    public AuthRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkLogin(String ip, String email) {
        Instant now = clock.instant();
        throwIfBlocked(Math.max(
                loginByIp.retryAfterSeconds(ip, now),
                loginByIpAndEmail.retryAfterSeconds(ipAndEmail(ip, email), now)));
    }

    public void loginFailed(String ip, String email) {
        Instant now = clock.instant();
        loginByIp.recordAttempt(ip, now);
        loginByIpAndEmail.recordAttempt(ipAndEmail(ip, email), now);
    }

    public void loginSucceeded(String ip, String email) {
        loginByIpAndEmail.reset(ipAndEmail(ip, email));
    }

    public void checkRegister(String ip) {
        throwIfBlocked(registerByIp.retryAfterSeconds(ip, clock.instant()));
    }

    public void registerAttempted(String ip) {
        registerByIp.recordAttempt(ip, clock.instant());
    }

    public void checkRefresh(String ip) {
        throwIfBlocked(refreshByIp.retryAfterSeconds(ip, clock.instant()));
    }

    public void refreshFailed(String ip) {
        refreshByIp.recordAttempt(ip, clock.instant());
    }

    // Só para os testes: o contexto Spring (e este bean) é partilhado entre eles.
    public void reset() {
        loginByIpAndEmail.clear();
        loginByIp.clear();
        registerByIp.clear();
        refreshByIp.clear();
    }

    private static AttemptLimiter backoff(int maxFailures) {
        return new AttemptLimiter(
                Policy.withBackoff(maxFailures, FAILURE_WINDOW, BASE_LOCK, MAX_LOCK, BACKOFF_MEMORY),
                MAX_ENTRIES_PER_LIMIT);
    }

    private static void throwIfBlocked(long retryAfterSeconds) {
        if (retryAfterSeconds > 0) {
            throw new TooManyAttemptsException(retryAfterSeconds);
        }
    }

    private static String ipAndEmail(String ip, String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return ip + "|" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
