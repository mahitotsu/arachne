package com.mahitotsu.arachne.strands.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Runtime-local hook registry that dispatches typed lifecycle events.
 */
public final class DispatchingHookRegistry implements HookRegistry, HookRegistrar {

    private final List<Consumer<BeforeInvocationEvent>> beforeInvocationCallbacks = new ArrayList<>();
    private final List<Consumer<AfterInvocationEvent>> afterInvocationCallbacks = new ArrayList<>();
    private final List<Consumer<MessageAddedEvent>> messageAddedCallbacks = new ArrayList<>();
    private final List<Consumer<BeforeModelCallEvent>> beforeModelCallCallbacks = new ArrayList<>();
    private final List<Consumer<AfterModelCallEvent>> afterModelCallCallbacks = new ArrayList<>();
    private final List<Consumer<BeforeToolCallEvent>> beforeToolCallCallbacks = new ArrayList<>();
    private final List<Consumer<AfterToolCallEvent>> afterToolCallCallbacks = new ArrayList<>();

    public DispatchingHookRegistry() {
    }

    public static DispatchingHookRegistry fromProviders(List<? extends HookProvider> providers) {
        DispatchingHookRegistry registry = new DispatchingHookRegistry();
        for (HookProvider provider : List.copyOf(Objects.requireNonNull(providers, "providers must not be null"))) {
            provider.registerHooks(registry);
        }
        return registry;
    }

    @Override
    public HookRegistrar beforeInvocation(Consumer<BeforeInvocationEvent> callback) {
        beforeInvocationCallbacks.add(Objects.requireNonNull(callback, "callback must not be null"));
        return this;
    }

    @Override
    public HookRegistrar afterInvocation(Consumer<AfterInvocationEvent> callback) {
        afterInvocationCallbacks.add(Objects.requireNonNull(callback, "callback must not be null"));
        return this;
    }

    @Override
    public HookRegistrar messageAdded(Consumer<MessageAddedEvent> callback) {
        messageAddedCallbacks.add(Objects.requireNonNull(callback, "callback must not be null"));
        return this;
    }

    @Override
    public HookRegistrar beforeModelCall(Consumer<BeforeModelCallEvent> callback) {
        beforeModelCallCallbacks.add(Objects.requireNonNull(callback, "callback must not be null"));
        return this;
    }

    @Override
    public HookRegistrar afterModelCall(Consumer<AfterModelCallEvent> callback) {
        afterModelCallCallbacks.add(Objects.requireNonNull(callback, "callback must not be null"));
        return this;
    }

    @Override
    public HookRegistrar beforeToolCall(Consumer<BeforeToolCallEvent> callback) {
        beforeToolCallCallbacks.add(Objects.requireNonNull(callback, "callback must not be null"));
        return this;
    }

    @Override
    public HookRegistrar afterToolCall(Consumer<AfterToolCallEvent> callback) {
        afterToolCallCallbacks.add(Objects.requireNonNull(callback, "callback must not be null"));
        return this;
    }

    @Override
    public BeforeInvocationEvent onBeforeInvocation(BeforeInvocationEvent event) {
        return dispatch(event, beforeInvocationCallbacks);
    }

    @Override
    public AfterInvocationEvent onAfterInvocation(AfterInvocationEvent event) {
        return dispatch(event, afterInvocationCallbacks);
    }

    @Override
    public MessageAddedEvent onMessageAdded(MessageAddedEvent event) {
        return dispatch(event, messageAddedCallbacks);
    }

    @Override
    public BeforeModelCallEvent onBeforeModelCall(BeforeModelCallEvent event) {
        return dispatch(event, beforeModelCallCallbacks);
    }

    @Override
    public AfterModelCallEvent onAfterModelCall(AfterModelCallEvent event) {
        return dispatch(event, afterModelCallCallbacks);
    }

    @Override
    public BeforeToolCallEvent onBeforeToolCall(BeforeToolCallEvent event) {
        return dispatch(event, beforeToolCallCallbacks);
    }

    @Override
    public AfterToolCallEvent onAfterToolCall(AfterToolCallEvent event) {
        return dispatch(event, afterToolCallCallbacks);
    }

    private static <T> T dispatch(T event, List<? extends Consumer<T>> callbacks) {
        for (Consumer<T> callback : callbacks) {
            callback.accept(event);
        }
        return event;
    }
}