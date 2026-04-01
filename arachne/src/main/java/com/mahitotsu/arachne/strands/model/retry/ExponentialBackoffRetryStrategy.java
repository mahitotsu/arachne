package com.mahitotsu.arachne.strands.model.retry;

import java.time.Duration;
import java.util.Objects;

import com.mahitotsu.arachne.strands.model.ModelRetryableException;

/**
 * Retries retryable model failures with exponential backoff.
 */
public class ExponentialBackoffRetryStrategy implements ModelRetryStrategy {

    public static final int DEFAULT_MAX_ATTEMPTS = 6;
    public static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(4);
    public static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(240);

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final Sleeper sleeper;

    public ExponentialBackoffRetryStrategy() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_DELAY, DEFAULT_MAX_DELAY);
    }

    public ExponentialBackoffRetryStrategy(int maxAttempts, Duration initialDelay, Duration maxDelay) {
        this(maxAttempts, initialDelay, maxDelay, Thread::sleep);
    }

    ExponentialBackoffRetryStrategy(int maxAttempts, Duration initialDelay, Duration maxDelay, Sleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must not be negative");
        }
        if (maxDelay.isNegative()) {
            throw new IllegalArgumentException("maxDelay must not be negative");
        }
        this.maxAttempts = maxAttempts;
        this.initialDelay = Objects.requireNonNull(initialDelay, "initialDelay must not be null");
        this.maxDelay = Objects.requireNonNull(maxDelay, "maxDelay must not be null");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
    }

    @Override
    public <T> T execute(ModelCall<T> modelCall) {
        Objects.requireNonNull(modelCall, "modelCall must not be null");

        int attempt = 1;
        while (true) {
            try {
                return modelCall.invoke();
            } catch (ModelRetryableException exception) {
                if (attempt >= maxAttempts) {
                    throw exception;
                }
                sleep(calculateDelay(attempt - 1));
                attempt++;
            }
        }
    }

    Duration calculateDelay(int retryIndex) {
        Duration delay = initialDelay.multipliedBy(1L << retryIndex);
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }

    private void sleep(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry model invocation", interruptedException);
        }
    }
}