package com.mahitotsu.arachne.strands.model.retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.strands.model.ModelException;
import com.mahitotsu.arachne.strands.model.ModelRetryableException;

class ExponentialBackoffRetryStrategyTest {

    @Test
    void retriesRetryableFailureUntilSuccess() {
        List<Duration> delays = new ArrayList<>();
        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy(
                4,
                Duration.ofMillis(10),
                Duration.ofMillis(25),
                delay -> delays.add(delay));
        AtomicInteger attempts = new AtomicInteger();

        String result = strategy.execute(() -> {
            if (attempts.getAndIncrement() < 2) {
                throw new ModelRetryableException("temporary");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
        assertThat(delays).containsExactly(Duration.ofMillis(10), Duration.ofMillis(20));
    }

    @Test
    void stopsAfterMaxAttempts() {
        List<Duration> delays = new ArrayList<>();
        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy(
                3,
                Duration.ofMillis(10),
                Duration.ofMillis(15),
                delay -> delays.add(delay));
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> strategy.execute(() -> {
            attempts.incrementAndGet();
            throw new ModelRetryableException("still failing");
        }))
                .isInstanceOf(ModelRetryableException.class)
                .hasMessageContaining("still failing");

        assertThat(attempts).hasValue(3);
        assertThat(delays).containsExactly(Duration.ofMillis(10), Duration.ofMillis(15));
    }

    @Test
    void doesNotRetryNonRetryableFailure() {
        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy(
                3,
                Duration.ZERO,
                Duration.ZERO,
                delay -> {
                });
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> strategy.execute(() -> {
            attempts.incrementAndGet();
            throw new ModelException("fatal");
        }))
                .isInstanceOf(ModelException.class)
                .hasMessageContaining("fatal");

        assertThat(attempts).hasValue(1);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new ExponentialBackoffRetryStrategy(0, Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }
}