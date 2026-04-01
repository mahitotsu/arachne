package com.mahitotsu.arachne.samples.streamingsteering;

import java.util.concurrent.atomic.AtomicInteger;

import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolResult;

final class PolicyLookupTool implements Tool {

    private final AtomicInteger invocations = new AtomicInteger();

    @Override
    public ToolSpec spec() {
        return new ToolSpec("policy_lookup", "Looks up live refund policy text", null);
    }

    @Override
    public ToolResult invoke(Object input) {
        invocations.incrementAndGet();
        return ToolResult.success("tool-1", "Live refund policy text");
    }

    int invocations() {
        return invocations.get();
    }
}