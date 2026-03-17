package io.arachne.strands.tool;

import io.arachne.strands.model.ToolSpec;

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
}
