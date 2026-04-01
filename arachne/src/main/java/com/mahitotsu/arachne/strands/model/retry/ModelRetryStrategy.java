package com.mahitotsu.arachne.strands.model.retry;

/**
 * Strategy interface for retrying model invocations.
 */
public interface ModelRetryStrategy {

    <T> T execute(ModelCall<T> modelCall);

    @FunctionalInterface
    interface ModelCall<T> {
        T invoke();
    }
}