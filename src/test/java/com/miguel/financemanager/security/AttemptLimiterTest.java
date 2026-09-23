package com.miguel.financemanager.security;

import com.miguel.financemanager.security.AttemptLimiter.Policy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AttemptLimiterTest {

    private static final Instant T0 = Instant.parse("2026-09-23T10:00:00Z");

    private static AttemptLimiter backoff(int max, int maxEntries) {
        return new AttemptLimiter(Policy.withBackoff(max, Duration.ofMinutes(15), Duration.ofSeconds(30),
                Duration.ofMinutes(15), Duration.ofHours(24)), maxEntries);
    }

    private static void fail(AttemptLimiter limiter, String key, Instant at, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordAttempt(key, at);
        }
    }

    @Test
    void locksAtThreshold_andNotBefore() {
        AttemptLimiter limiter = backoff(5, 100);

        fail(limiter, "k", T0, 4);
        assertThat(limiter.retryAfterSeconds("k", T0)).isZero();

        fail(limiter, "k", T0, 1);
        assertThat(limiter.retryAfterSeconds("k", T0)).isEqualTo(30);
        assertThat(limiter.retryAfterSeconds("k", T0.plusSeconds(12))).isEqualTo(18);
        assertThat(limiter.retryAfterSeconds("k", T0.plusSeconds(30))).isZero();
    }

    @Test
    void retryAfter_roundsUp() {
        AttemptLimiter limiter = backoff(1, 100);
        limiter.recordAttempt("k", T0);

        assertThat(limiter.retryAfterSeconds("k", T0.plusMillis(29_001))).isEqualTo(1);
    }

    @Test
    void backoff_doublesEachLock_upToMax() {
        AttemptLimiter limiter = backoff(5, 100);
        Instant now = T0;
        long[] expected = {30, 60, 120, 240, 480, 900, 900};

        for (long seconds : expected) {
            fail(limiter, "k", now, 5);
            assertThat(limiter.retryAfterSeconds("k", now)).isEqualTo(seconds);
            now = now.plusSeconds(seconds);
            assertThat(limiter.retryAfterSeconds("k", now)).isZero();
        }
    }

    @Test
    void backoff_isForgottenAfterQuietPeriod() {
        AttemptLimiter limiter = backoff(5, 100);
        fail(limiter, "k", T0, 5);
        fail(limiter, "k", T0.plusSeconds(30), 5); // segundo bloqueio: 60 s

        Instant later = T0.plus(Duration.ofHours(25));
        fail(limiter, "k", later, 5);

        assertThat(limiter.retryAfterSeconds("k", later)).isEqualTo(30);
    }

    @Test
    void failuresOutsideWindow_doNotAccumulate() {
        AttemptLimiter limiter = backoff(5, 100);
        fail(limiter, "k", T0, 4);

        Instant later = T0.plus(Duration.ofMinutes(16));
        fail(limiter, "k", later, 4);

        assertThat(limiter.retryAfterSeconds("k", later)).isZero();
    }

    @Test
    void reset_clearsKey() {
        AttemptLimiter limiter = backoff(5, 100);
        fail(limiter, "k", T0, 4);
        limiter.reset("k");
        fail(limiter, "k", T0, 4);

        assertThat(limiter.retryAfterSeconds("k", T0)).isZero();
    }

    @Test
    void fixedWindow_locksUntilWindowEnds_thenAllowsAgain() {
        AttemptLimiter limiter = new AttemptLimiter(Policy.fixedWindow(10, Duration.ofHours(1)), 100);

        for (int i = 0; i < 10; i++) {
            limiter.recordAttempt("ip", T0.plus(Duration.ofMinutes(i)));
        }

        Instant tenth = T0.plus(Duration.ofMinutes(9));
        assertThat(limiter.retryAfterSeconds("ip", tenth)).isEqualTo(Duration.ofMinutes(51).toSeconds());

        Instant afterWindow = T0.plus(Duration.ofHours(1));
        assertThat(limiter.retryAfterSeconds("ip", afterWindow)).isZero();
        limiter.recordAttempt("ip", afterWindow);
        assertThat(limiter.retryAfterSeconds("ip", afterWindow)).isZero();
    }

    @Test
    void keysAreIndependent() {
        AttemptLimiter limiter = backoff(5, 100);
        fail(limiter, "a", T0, 5);

        assertThat(limiter.retryAfterSeconds("a", T0)).isPositive();
        assertThat(limiter.retryAfterSeconds("b", T0)).isZero();
    }

    // Muitas chaves diferentes (IPs/emails gerados) não fazem crescer a memória
    // acima do teto, e chaves novas continuam a ser contadas (não rejeitadas).
    @Test
    void manyDistinctKeys_stayWithinCap_andNewKeysStillCount() {
        AttemptLimiter limiter = backoff(5, 100);

        for (int i = 0; i < 50_000; i++) {
            limiter.recordAttempt("flood-" + i, T0);
        }
        assertThat(limiter.size()).isEqualTo(100);

        fail(limiter, "victim", T0, 5);
        assertThat(limiter.retryAfterSeconds("victim", T0)).isEqualTo(30);
        assertThat(limiter.size()).isEqualTo(100);
    }

    @Test
    void atCap_evictsLeastRecentlyUsedKey() {
        AttemptLimiter limiter = backoff(5, 3);
        fail(limiter, "old", T0, 5);
        limiter.recordAttempt("b", T0);
        limiter.recordAttempt("c", T0);

        limiter.recordAttempt("d", T0);

        assertThat(limiter.size()).isEqualTo(3);
        assertThat(limiter.retryAfterSeconds("old", T0)).isZero();
    }

    @Test
    void idleKeys_areSwept() {
        AttemptLimiter limiter = backoff(5, 100);
        fail(limiter, "a", T0, 2);
        fail(limiter, "b", T0, 2);

        limiter.recordAttempt("c", T0.plus(Duration.ofMinutes(20)));

        assertThat(limiter.size()).isEqualTo(1);
    }

    @Test
    void lockedKeys_areNotSweptBeforeBackoffIsForgotten() {
        AttemptLimiter limiter = backoff(5, 100);
        fail(limiter, "locked", T0, 5);

        Instant later = T0.plus(Duration.ofHours(2));
        limiter.recordAttempt("other", later);
        assertThat(limiter.size()).isEqualTo(2);

        limiter.recordAttempt("other", T0.plus(Duration.ofHours(25)));
        assertThat(limiter.size()).isEqualTo(1);
    }
}
