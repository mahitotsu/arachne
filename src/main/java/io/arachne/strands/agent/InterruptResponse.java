package io.arachne.strands.agent;

import java.util.Objects;

/**
 * External response used to resume a pending interrupt.
 */
public record InterruptResponse(String interruptId, Object response) {

    public InterruptResponse {
        Objects.requireNonNull(interruptId, "interruptId must not be null");
    }
}