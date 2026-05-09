package com.mahitotsu.arachne.samples.domainseparation.workflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.mahitotsu.arachne.strands.agent.AgentState;
import com.mahitotsu.arachne.strands.hooks.AfterInvocationEvent;
import com.mahitotsu.arachne.strands.hooks.AfterModelCallEvent;
import com.mahitotsu.arachne.strands.hooks.AfterToolCallEvent;
import com.mahitotsu.arachne.strands.hooks.BeforeInvocationEvent;
import com.mahitotsu.arachne.strands.hooks.BeforeModelCallEvent;
import com.mahitotsu.arachne.strands.hooks.BeforeToolCallEvent;
import com.mahitotsu.arachne.strands.hooks.HookRegistrar;
import com.mahitotsu.arachne.strands.model.ToolSpec;

class DomainSeparationApprovalHookTest {

    @Test
    void approvedResumeForcesExecutionToolSelectionWhenExecutionIsMissing() {
        DomainSeparationApprovalHook hook = new DomainSeparationApprovalHook(new ObjectMapper());
        CapturingHookRegistrar registrar = new CapturingHookRegistrar();
        hook.registerHooks(registrar);

        AgentState state = new AgentState();
        state.put(DomainSeparationWorkflowState.APPROVAL, Map.of(
                "required", true,
                "status", "APPROVED",
                "approved", true,
                "approverId", "approver-2",
                "comment", "Approved"));
        state.put(DomainSeparationWorkflowState.PREPARATION, Map.of(
                "phase", "preparation",
                "preparedStatus", "LOCKED"));

        BeforeModelCallEvent event = new BeforeModelCallEvent(
                List.of(),
                List.of(new ToolSpec("execute_account_operation", "Execute operation", null)),
                "coordinator",
                null,
            null,
                state);

        registrar.beforeModelCall.accept(event);

        assertThat(event.toolSelection()).isNotNull();
        assertThat(event.toolSelection().toolName()).isEqualTo("execute_account_operation");
    }

    @Test
    void approvedResumeDoesNotForceExecutionAfterExecutionStateIsPresent() {
        DomainSeparationApprovalHook hook = new DomainSeparationApprovalHook(new ObjectMapper());
        CapturingHookRegistrar registrar = new CapturingHookRegistrar();
        hook.registerHooks(registrar);

        AgentState state = new AgentState();
        state.put(DomainSeparationWorkflowState.APPROVAL, Map.of(
                "required", true,
                "status", "APPROVED",
                "approved", true,
                "approverId", "approver-2",
                "comment", "Approved"));
        state.put(DomainSeparationWorkflowState.PREPARATION, Map.of(
                "phase", "preparation",
                "preparedStatus", "LOCKED"));
        state.put(DomainSeparationWorkflowState.EXECUTION, new LinkedHashMap<>(Map.of(
                "phase", "execution",
                "outcome", "UNLOCKED")));

        BeforeModelCallEvent event = new BeforeModelCallEvent(
                List.of(),
                List.of(new ToolSpec("execute_account_operation", "Execute operation", null)),
                "coordinator",
                null,
            null,
                state);

        registrar.beforeModelCall.accept(event);

        assertThat(event.toolSelection()).isNull();
    }

    private static final class CapturingHookRegistrar implements HookRegistrar {

        private Consumer<BeforeInvocationEvent> beforeInvocation = event -> {
        };
        private Consumer<AfterInvocationEvent> afterInvocation = event -> {
        };
        private Consumer<BeforeModelCallEvent> beforeModelCall = event -> {
        };
        private Consumer<AfterModelCallEvent> afterModelCall = event -> {
        };
        private Consumer<BeforeToolCallEvent> beforeToolCall = event -> {
        };
        private Consumer<AfterToolCallEvent> afterToolCall = event -> {
        };

        @Override
        public HookRegistrar beforeInvocation(Consumer<BeforeInvocationEvent> callback) {
            this.beforeInvocation = callback;
            return this;
        }

        @Override
        public HookRegistrar afterInvocation(Consumer<AfterInvocationEvent> callback) {
            this.afterInvocation = callback;
            return this;
        }

        @Override
        public HookRegistrar messageAdded(Consumer<com.mahitotsu.arachne.strands.hooks.MessageAddedEvent> callback) {
            return this;
        }

        @Override
        public HookRegistrar beforeModelCall(Consumer<BeforeModelCallEvent> callback) {
            this.beforeModelCall = callback;
            return this;
        }

        @Override
        public HookRegistrar afterModelCall(Consumer<AfterModelCallEvent> callback) {
            this.afterModelCall = callback;
            return this;
        }

        @Override
        public HookRegistrar beforeToolCall(Consumer<BeforeToolCallEvent> callback) {
            this.beforeToolCall = callback;
            return this;
        }

        @Override
        public HookRegistrar afterToolCall(Consumer<AfterToolCallEvent> callback) {
            this.afterToolCall = callback;
            return this;
        }
    }
}
