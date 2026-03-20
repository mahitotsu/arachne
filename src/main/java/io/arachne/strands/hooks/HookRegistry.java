package io.arachne.strands.hooks;

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
    default BeforeInvocationEvent onBeforeInvocation(BeforeInvocationEvent event) {
        return event;
    }

    /** Called after the agent has produced its final response. */
    default AfterInvocationEvent onAfterInvocation(AfterInvocationEvent event) {
        return event;
    }

    /** Called after a message is appended to the conversation history. */
    default MessageAddedEvent onMessageAdded(MessageAddedEvent event) {
        return event;
    }

    /** Called immediately before each model call inside the event loop. */
    default BeforeModelCallEvent onBeforeModelCall(BeforeModelCallEvent event) {
        return event;
    }

    /** Called immediately after each model call returns. */
    default AfterModelCallEvent onAfterModelCall(AfterModelCallEvent event) {
        return event;
    }

    /** Called before a tool is executed. */
    default BeforeToolCallEvent onBeforeToolCall(BeforeToolCallEvent event) {
        return event;
    }

    /** Called after a tool completes (success or error). */
    default AfterToolCallEvent onAfterToolCall(AfterToolCallEvent event) {
        return event;
    }
}
