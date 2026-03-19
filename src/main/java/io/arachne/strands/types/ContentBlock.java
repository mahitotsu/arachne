package io.arachne.strands.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A single piece of content within a {@link Message}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ContentBlock.Text.class, name = "text"),
    @JsonSubTypes.Type(value = ContentBlock.ToolUse.class, name = "toolUse"),
    @JsonSubTypes.Type(value = ContentBlock.ToolResult.class, name = "toolResult")
})
public sealed interface ContentBlock
        permits ContentBlock.Text, ContentBlock.ToolUse, ContentBlock.ToolResult {

    record Text(String text) implements ContentBlock {}

    record ToolUse(String toolUseId, String name, Object input) implements ContentBlock {}

    record ToolResult(String toolUseId, Object content, String status) implements ContentBlock {}

    static ContentBlock text(String text) {
        return new Text(text);
    }
}
