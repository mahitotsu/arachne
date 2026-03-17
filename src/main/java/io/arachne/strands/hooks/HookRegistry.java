package io.arachne.strands.hooks;

import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.tool.ToolResult;
import io.arachne.strands.types.Message;

import java.util.List;

/**
 * Lifecycle hook registry for an agent invocation.
 *
 * <p>Phase 1: all methods are no-op callsites.
 * Phase 4 will add {@code HookProvider} registration and full event dispatch.
 *
 * <p>Corresponds to {@code strands.hooks.HookRegistry} in the Python SDK.
 */
public interface HookRegistry {

    /** Called before the agent processes a user prompt. */
    default void onBeforeInvocation(String prompt) {}

    /** Called after the agent has produced its final response. */
    default void onAfterInvocation(String text) {}

    /** Called immediately before each model call inside the event loop. */
    default void onBeforeModelCall(List<Message> messages, List<ToolSpec> toolSpecs) {}

    /** Called immediately after each model call returns. */
    default void onAfterModelCall(Message response, String stopReason) {}

    /** Called before a tool is executed. */
    default void onBeforeToolCall(String toolName, String toolUseId, Object input) {}

    /** Called after a tool completes (success or error). */
    default void onAfterToolCall(String toolName, String toolUseId, ToolResult result) {}
}
