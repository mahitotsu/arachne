package com.mahitotsu.arachne.strands.steering;

import java.util.Objects;

public record Guide(String reason) implements ToolSteeringAction, ModelSteeringAction {

    public Guide {
        Objects.requireNonNull(reason, "reason must not be null");
    }
}