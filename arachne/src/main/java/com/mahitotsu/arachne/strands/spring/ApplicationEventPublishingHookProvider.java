package com.mahitotsu.arachne.strands.spring;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;

import com.mahitotsu.arachne.strands.hooks.HookProvider;
import com.mahitotsu.arachne.strands.hooks.HookRegistrar;

/**
 * Publishes observation-only Spring events for hook activity.
 */
@ArachneHook
public final class ApplicationEventPublishingHookProvider implements HookProvider {

    private final ApplicationEventPublisher applicationEventPublisher;

    public ApplicationEventPublishingHookProvider(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = Objects.requireNonNull(applicationEventPublisher, "applicationEventPublisher must not be null");
    }

    @Override
    public void registerHooks(HookRegistrar registrar) {
        registrar.beforeInvocation(event -> publish(
                "beforeInvocation",
                new ArachneLifecycleApplicationEvent.InvocationObservation(
                        "before",
                        event.prompt(),
                        null,
                        null,
                        List.copyOf(event.messages()),
                        Map.copyOf(event.state().get()))));
        registrar.afterInvocation(event -> publish(
                "afterInvocation",
                new ArachneLifecycleApplicationEvent.InvocationObservation(
                        "after",
                        null,
                        event.text(),
                        event.stopReason(),
                        List.copyOf(event.messages()),
                        Map.copyOf(event.state().get()))));
        registrar.messageAdded(event -> publish(
                "messageAdded",
                new ArachneLifecycleApplicationEvent.MessageAddedObservation(
                        event.message(),
                        List.copyOf(event.messages()),
                        Map.copyOf(event.state().get()))));
        registrar.beforeModelCall(event -> publish(
                "beforeModelCall",
                new ArachneLifecycleApplicationEvent.ModelCallObservation(
                        "before",
                        List.copyOf(event.messages()),
                        List.copyOf(event.toolSpecs()),
                        event.systemPrompt(),
                        null,
                        null,
                        Map.copyOf(event.state().get()))));
        registrar.afterModelCall(event -> publish(
                "afterModelCall",
                new ArachneLifecycleApplicationEvent.ModelCallObservation(
                        "after",
                        List.copyOf(event.messages()),
                        List.of(),
                        null,
                        event.response(),
                        event.stopReason(),
                        Map.copyOf(event.state().get()))));
        registrar.beforeToolCall(event -> publish(
                "beforeToolCall",
                new ArachneLifecycleApplicationEvent.ToolCallObservation(
                        "before",
                        event.toolName(),
                        event.toolUseId(),
                        event.input(),
                        null,
                        Map.copyOf(event.state().get()))));
        registrar.afterToolCall(event -> publish(
                "afterToolCall",
                new ArachneLifecycleApplicationEvent.ToolCallObservation(
                        "after",
                        event.toolName(),
                        event.toolUseId(),
                        null,
                        event.result(),
                        Map.copyOf(event.state().get()))));
    }

    private void publish(String type, Object payload) {
        applicationEventPublisher.publishEvent(new ArachneLifecycleApplicationEvent(this, type, payload));
    }
}