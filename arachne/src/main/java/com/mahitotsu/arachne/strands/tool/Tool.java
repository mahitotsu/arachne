package com.mahitotsu.arachne.strands.tool;

import com.mahitotsu.arachne.strands.model.ToolSpec;

/**
 * A callable tool that an agent can invoke.
 *
 * <p>Corresponds to {@code strands.tools.Tool} in the Python SDK.
 * Implementations can be plain Java methods annotated with {@code @StrandsTool},
 * or programmatic implementations of this interface.
 */
public interface Tool {

    /**
     * Schema and metadata described to the model.
     */
    ToolSpec spec();

    /**
     * Execute the tool with the given input (JSON-compatible object).
     *
     * @param input parsed input matching the tool's JSON schema
     * @return result content (text, structured data, etc.)
     */
    ToolResult invoke(Object input);

    /**
     * Execute the tool with logical invocation metadata.
     *
     * <p>The default implementation preserves the original contract for tools that do not
     * care about invocation metadata.
     *
     * @param input parsed input matching the tool's JSON schema
     * @param context logical tool invocation metadata
     * @return result content (text, structured data, etc.)
     */
    default ToolResult invoke(Object input, ToolInvocationContext context) {
        return invoke(input);
    }
}
