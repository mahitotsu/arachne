package com.mahitotsu.arachne.strands.eventloop;

import com.mahitotsu.arachne.strands.tool.StructuredOutputException;
import com.mahitotsu.arachne.strands.tool.StructuredOutputTool;

/**
 * Tracks forced structured-output retries for a single invocation.
 */
public class StructuredOutputContext<T> {

    private final StructuredOutputTool<T> tool;
    private boolean forceAttempted;

    public StructuredOutputContext(StructuredOutputTool<T> tool) {
        this.tool = tool;
    }

    public StructuredOutputTool<T> tool() {
        return tool;
    }

    public boolean isSatisfied() {
        return tool.hasValue();
    }

    public boolean forceAttempted() {
        return forceAttempted;
    }

    public void markForceAttempted() {
        this.forceAttempted = true;
    }

    public String forcePrompt() {
        return tool.forcePrompt();
    }

    public String forcedToolName() {
        return tool.toolName();
    }

    public T requireValue() {
        if (!isSatisfied()) {
            throw new StructuredOutputException("Structured output was not produced");
        }
        return tool.requireValue();
    }
}