package io.arachne.strands.hooks;

import java.util.function.Consumer;

/**
 * Registration API for hook callbacks.
 */
public interface HookRegistrar {

    HookRegistrar beforeInvocation(Consumer<BeforeInvocationEvent> callback);

    HookRegistrar afterInvocation(Consumer<AfterInvocationEvent> callback);

    HookRegistrar messageAdded(Consumer<MessageAddedEvent> callback);

    HookRegistrar beforeModelCall(Consumer<BeforeModelCallEvent> callback);

    HookRegistrar afterModelCall(Consumer<AfterModelCallEvent> callback);

    HookRegistrar beforeToolCall(Consumer<BeforeToolCallEvent> callback);

    HookRegistrar afterToolCall(Consumer<AfterToolCallEvent> callback);
}