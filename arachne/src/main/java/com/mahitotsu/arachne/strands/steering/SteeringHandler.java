package com.mahitotsu.arachne.strands.steering;

import java.util.Objects;

import com.mahitotsu.arachne.strands.hooks.AfterInvocationEvent;
import com.mahitotsu.arachne.strands.hooks.AfterModelCallEvent;
import com.mahitotsu.arachne.strands.hooks.AfterToolCallEvent;
import com.mahitotsu.arachne.strands.hooks.BeforeInvocationEvent;
import com.mahitotsu.arachne.strands.hooks.BeforeModelCallEvent;
import com.mahitotsu.arachne.strands.hooks.BeforeToolCallEvent;
import com.mahitotsu.arachne.strands.hooks.HookRegistrar;
import com.mahitotsu.arachne.strands.hooks.MessageAddedEvent;
import com.mahitotsu.arachne.strands.hooks.Plugin;

/**
 * Base plugin for context-aware steering on top of the existing hook boundary.
 */
public abstract class SteeringHandler implements Plugin {

    @Override
    public final void registerHooks(HookRegistrar registrar) {
        registrar.beforeInvocation(this::onBeforeInvocation)
                .afterInvocation(this::onAfterInvocation)
                .messageAdded(this::onMessageAdded)
                .beforeModelCall(this::onBeforeModelCall)
                .afterModelCall(this::handleAfterModelCall)
                .beforeToolCall(this::handleBeforeToolCall)
                .afterToolCall(this::onAfterToolCall);
    }

    protected void onBeforeInvocation(BeforeInvocationEvent event) {
    }

    protected void onAfterInvocation(AfterInvocationEvent event) {
    }

    protected void onMessageAdded(MessageAddedEvent event) {
    }

    protected void onBeforeModelCall(BeforeModelCallEvent event) {
    }

    protected void onAfterToolCall(AfterToolCallEvent event) {
    }

    protected ToolSteeringAction steerBeforeTool(BeforeToolCallEvent event) {
        return new Proceed("Default implementation: allowing tool execution");
    }

    protected ModelSteeringAction steerAfterModel(AfterModelCallEvent event) {
        return new Proceed("Default implementation: accepting model response");
    }

    private void handleBeforeToolCall(BeforeToolCallEvent event) {
        ToolSteeringAction action = Objects.requireNonNull(steerBeforeTool(event), "tool steering action must not be null");
        if (action instanceof Guide guide) {
            event.guide(guide.reason());
        } else if (action instanceof Interrupt interrupt) {
            event.interrupt("steering_input_" + event.toolName(), java.util.Map.of("message", interrupt.reason()));
        }
    }

    private void handleAfterModelCall(AfterModelCallEvent event) {
        ModelSteeringAction action = Objects.requireNonNull(steerAfterModel(event), "model steering action must not be null");
        if (action instanceof Guide guide) {
            event.retryWithGuidance(guide.reason());
        }
    }
}