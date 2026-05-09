package com.mahitotsu.arachne.samples.streamingsteering;

import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolResult;

final class PolicyLookupTool implements Tool {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final AtomicInteger invocations = new AtomicInteger();

    @Override
    public ToolSpec spec() {
        return new ToolSpec("policy_lookup", "Looks up live refund policy text", inputSchema());
    }

    @Override
    public ToolResult invoke(Object input) {
        invocations.incrementAndGet();
        return ToolResult.success("tool-1", "Live refund policy text");
    }

    int invocations() {
        return invocations.get();
    }

    private static ObjectNode inputSchema() {
        ObjectNode root = JSON.objectNode();
        root.put("type", "object");
        root.putObject("properties");
        root.put("additionalProperties", false);
        return root;
    }
}