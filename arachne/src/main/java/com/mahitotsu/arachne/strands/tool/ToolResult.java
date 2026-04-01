package com.mahitotsu.arachne.strands.tool;

/**
 * Result returned by a tool invocation.
 */
public record ToolResult(
        String toolUseId,
        ToolStatus status,
        Object content
) {

    public enum ToolStatus {
        SUCCESS, ERROR
    }

    public static ToolResult success(String toolUseId, Object content) {
        return new ToolResult(toolUseId, ToolStatus.SUCCESS, content);
    }

    public static ToolResult error(String toolUseId, String message) {
        return new ToolResult(toolUseId, ToolStatus.ERROR, message);
    }
}
