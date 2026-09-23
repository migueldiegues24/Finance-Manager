package com.miguel.financemanager.security;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Contador de tentativas por chave (IP, IP+email...), em memória, para uma
// só instância da app. Ao chegar a maxAttempts dentro da janela, a chave fica
// bloqueada:
//  - com baseLock: 30 s, 1 min, 2 min... (duplica a cada bloqueio, até
//    maxLock); passado o bloqueio o contador recomeça do zero, e o nível de
//    backoff esquece-se ao fim de backoffMemory sem tentativas;
//  - sem baseLock: até ao fim da janela (limite fixo, ex.: 10 por hora).
//
// Memória limitada: no máximo maxEntries chaves. Acima disso sai a chave
// usada há mais tempo (LRU), por isso nunca se rejeitam chaves novas (o que
// deixaria um atacante bloquear toda a gente enchendo o mapa). Entradas
// sem bloqueio nem janela ativa são limpas uma vez por minuto.
public class AttemptLimiter {

    public record Policy(int maxAttempts, Duration window, Duration baseLock, Duration maxLock,
                         Duration backoffMemory) {

        public static Policy withBackoff(int maxAttempts, Duration window, Duration baseLock,
                                         Duration maxLock, Duration backoffMemory) {
            return new Policy(maxAttempts, window, baseLock, maxLock, backoffMemory);
        }

        public static Policy fixedWindow(int maxAttempts, Duration window) {
            return new Policy(maxAttempts, window, null, null, Duration.ZERO);
        }

        boolean hasBackoff() {
            return baseLock != null;
        }
    }

    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);
    // 30 s * 2^20 já passa de qualquer maxLock razoável; evita overflow.
    private static final int MAX_LOCK_LEVEL = 20;

    private static final class Counter {
        int attempts;
        Instant windowStart;
        Instant lastAttempt;
        Instant lockedUntil = Instant.MIN;
        int lockLevel;
    }

    private final Policy policy;
    private final Map<String, Counter> entries;
    private Instant nextSweep = Instant.MIN;

    public AttemptLimiter(Policy policy, int maxEntries) {
        this.policy = policy;
        // accessOrder = true: a ordem de iteração é a do último acesso (LRU).
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Counter> eldest) {
                return size() > maxEntries;
            }
        };
    }

    // Segundos até a chave poder tentar de novo (arredondado para cima), ou 0.
    public synchronized long retryAfterSeconds(String key, Instant now) {
        Counter entry = entries.get(key);
        if (entry == null || !now.isBefore(entry.lockedUntil)) {
            return 0;
        }
        long millis = Duration.between(now, entry.lockedUntil).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    public synchronized void recordAttempt(String key, Instant now) {
        sweepIfDue(now);

        Counter entry = entries.get(key);
        if (entry == null) {
            entry = new Counter();
            entry.windowStart = now;
            entries.put(key, entry);
        }

        if (!now.isBefore(entry.windowStart.plus(policy.window()))) {
            entry.attempts = 0;
            entry.windowStart = now;
        }
        if (entry.lockLevel > 0 && !now.isBefore(entry.lastAttempt.plus(policy.backoffMemory()))) {
            entry.lockLevel = 0;
        }

        entry.attempts++;
        entry.lastAttempt = now;

        if (entry.attempts < policy.maxAttempts()) {
            return;
        }

        if (policy.hasBackoff()) {
            Duration lock = policy.baseLock().multipliedBy(1L << entry.lockLevel);
            if (lock.compareTo(policy.maxLock()) > 0) {
                lock = policy.maxLock();
            }
            entry.lockedUntil = now.plus(lock);
            entry.lockLevel = Math.min(entry.lockLevel + 1, MAX_LOCK_LEVEL);
            entry.attempts = 0;
            entry.windowStart = now;
        } else {
            // Limite fixo: bloqueia até a janela acabar; aí o contador recomeça.
            entry.lockedUntil = entry.windowStart.plus(policy.window());
        }
    }

    public synchronized void reset(String key) {
        entries.remove(key);
    }

    public synchronized void clear() {
        entries.clear();
        nextSweep = Instant.MIN;
    }

    synchronized int size() {
        return entries.size();
    }

    private void sweepIfDue(Instant now) {
        if (now.isBefore(nextSweep)) {
            return;
        }
        nextSweep = now.plus(SWEEP_INTERVAL);
        entries.values().removeIf(entry -> isIdle(entry, now));
    }

    private boolean isIdle(Counter entry, Instant now) {
        return !now.isBefore(entry.lockedUntil)
                && !now.isBefore(entry.windowStart.plus(policy.window()))
                && (entry.lockLevel == 0 || !now.isBefore(entry.lastAttempt.plus(policy.backoffMemory())));
    }
}
