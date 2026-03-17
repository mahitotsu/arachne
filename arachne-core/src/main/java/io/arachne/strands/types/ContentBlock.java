package io.arachne.strands.types;

/**
 * A single piece of content within a {@link Message}.
 */
public sealed interface ContentBlock
        permits ContentBlock.Text, ContentBlock.ToolUse, ContentBlock.ToolResult {

    record Text(String text) implements ContentBlock {}

    record ToolUse(String toolUseId, String name, Object input) implements ContentBlock {}

    record ToolResult(String toolUseId, Object content, String status) implements ContentBlock {}

    static ContentBlock text(String text) {
        return new Text(text);
    }
}
