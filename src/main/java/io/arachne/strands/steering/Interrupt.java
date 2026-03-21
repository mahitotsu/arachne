package io.arachne.strands.steering;

import java.util.Objects;

public record Interrupt(String reason) implements ToolSteeringAction {

    public Interrupt {
        Objects.requireNonNull(reason, "reason must not be null");
    }
}