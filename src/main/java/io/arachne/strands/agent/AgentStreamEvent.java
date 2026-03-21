package io.arachne.strands.agent;

import io.arachne.strands.tool.ToolResult;

/**
 * Events emitted during {@link Agent#stream(String, java.util.function.Consumer)}.
 */
public sealed interface AgentStreamEvent
        permits AgentStreamEvent.TextDelta,
                AgentStreamEvent.ToolUseRequested,
                AgentStreamEvent.ToolResultObserved,
                AgentStreamEvent.Retry,
                AgentStreamEvent.Complete {

    record TextDelta(String delta) implements AgentStreamEvent {}

    record ToolUseRequested(String toolUseId, String toolName, Object input) implements AgentStreamEvent {}

    record ToolResultObserved(ToolResult result) implements AgentStreamEvent {}

    record Retry(String guidance) implements AgentStreamEvent {}

    record Complete(AgentResult result) implements AgentStreamEvent {}
}