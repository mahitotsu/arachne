package io.arachne.strands.steering;

import java.util.Objects;

public record Proceed(String reason) implements ToolSteeringAction, ModelSteeringAction {

    public Proceed {
        Objects.requireNonNull(reason, "reason must not be null");
    }
}