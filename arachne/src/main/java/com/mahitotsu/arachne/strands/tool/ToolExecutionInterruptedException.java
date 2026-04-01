package com.mahitotsu.arachne.strands.tool;

import java.util.List;
import java.util.Objects;

import com.mahitotsu.arachne.strands.agent.AgentInterrupt;

/**
 * Internal control-flow exception used when tool execution is paused by interrupts.
 */
public final class ToolExecutionInterruptedException extends RuntimeException {

    private final List<AgentInterrupt> interrupts;

    public ToolExecutionInterruptedException(List<AgentInterrupt> interrupts) {
        super("Tool execution interrupted");
        this.interrupts = List.copyOf(Objects.requireNonNull(interrupts, "interrupts must not be null"));
    }

    public List<AgentInterrupt> interrupts() {
        return interrupts;
    }
}