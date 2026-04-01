package com.mahitotsu.arachne.samples.domainseparation.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.mahitotsu.arachne.samples.domainseparation.domain.AccountOperationExecution;
import com.mahitotsu.arachne.samples.domainseparation.domain.AccountOperationPreparation;
import com.mahitotsu.arachne.samples.domainseparation.domain.AccountOperationRequest;
import com.mahitotsu.arachne.samples.domainseparation.domain.AccountOperationWorkflowSummary;
import com.mahitotsu.arachne.samples.domainseparation.domain.AccountWorkflowApproval;
import com.mahitotsu.arachne.samples.domainseparation.domain.ApprovalDecision;
import com.mahitotsu.arachne.samples.domainseparation.security.OperatorAuthorizationContext;
import com.mahitotsu.arachne.samples.domainseparation.security.OperatorAuthorizationContextHolder;
import com.mahitotsu.arachne.samples.domainseparation.workflow.DomainSeparationWorkflowState;
import com.mahitotsu.arachne.strands.agent.Agent;
import com.mahitotsu.arachne.strands.agent.AgentInterrupt;
import com.mahitotsu.arachne.strands.agent.AgentResult;
import com.mahitotsu.arachne.strands.agent.AgentState;
import com.mahitotsu.arachne.strands.agent.InterruptResponse;
import com.mahitotsu.arachne.strands.session.AgentSession;
import com.mahitotsu.arachne.strands.session.SessionManager;
import com.mahitotsu.arachne.strands.skills.Skill;
import com.mahitotsu.arachne.strands.spring.AgentFactory;

@Service
public class DomainSeparationWorkflowService {

    private final AgentFactory agentFactory;
    private final ObjectMapper objectMapper;
    private final OperatorAuthorizationContextHolder authorizationContextHolder;
    private final SessionManager sessionManager;
    private final List<Skill> coordinatorSkills;

    public DomainSeparationWorkflowService(
            AgentFactory agentFactory,
            ObjectMapper objectMapper,
            OperatorAuthorizationContextHolder authorizationContextHolder,
            SessionManager sessionManager,
            @Qualifier("domainSeparationCoordinatorSkills") List<Skill> coordinatorSkills) {
        this.agentFactory = agentFactory;
        this.objectMapper = objectMapper;
        this.authorizationContextHolder = authorizationContextHolder;
        this.sessionManager = sessionManager;
        this.coordinatorSkills = coordinatorSkills;
    }

    public AccountOperationWorkflowSummary startWorkflow(
            String workflowId,
            AccountOperationRequest request,
            String reason,
            OperatorAuthorizationContext authorizationContext) {
        return withAuthorization(authorizationContext, () -> {
            Agent coordinator = coordinator(workflowId);
            AgentResult result = coordinator.run(startPrompt(request, reason));
            return summaryFrom(workflowId, coordinator.getState(), result.interrupted());
        });
    }

    public AccountOperationWorkflowSummary resumeWorkflow(
            String workflowId,
            ApprovalDecision decision,
            OperatorAuthorizationContext authorizationContext) {
        if (!decision.approved()) {
            return rejectWorkflow(workflowId, decision);
        }
        return withAuthorization(authorizationContext, () -> {
            Agent coordinator = coordinator(workflowId);
            AgentInterrupt interrupt = coordinator.getPendingInterrupts().stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No persisted pending interrupt found for workflow: " + workflowId));
            AgentResult result = coordinator.resume(new InterruptResponse(interrupt.id(), approvalResponse(decision)));
            return summaryFrom(workflowId, coordinator.getState(), result.interrupted());
        });
    }

    public int restoredMessageCount(String workflowId) {
        return coordinator(workflowId).getMessages().size();
    }

    private Agent coordinator(String workflowId) {
        return agentFactory.builder("operations-coordinator")
                .skills(coordinatorSkills)
                .sessionId(workflowId)
                .build();
    }

    private AccountOperationWorkflowSummary rejectWorkflow(String workflowId, ApprovalDecision decision) {
        AgentSession session = sessionManager.load(workflowId);
        if (session == null) {
            throw new IllegalStateException("No persisted workflow session found for: " + workflowId);
        }

        LinkedHashMap<String, Object> state = new LinkedHashMap<>(session.state());
        state.put(DomainSeparationWorkflowState.APPROVAL, approvalState("REJECTED", false, decision.approverId(), decision.comment()));
        state.put(DomainSeparationWorkflowState.STATUS, "REJECTED");
        state.remove(DomainSeparationWorkflowState.EXECUTION);

        sessionManager.save(workflowId, new AgentSession(session.messages(), state, session.conversationManagerState()));
        return summaryFrom(workflowId, new AgentState(state), false);
    }

    private String startPrompt(AccountOperationRequest request, String reason) {
        return String.join("\n",
                "mode=start",
                "operationType=" + request.operationType(),
                "accountId=" + request.accountId(),
                "requestedBy=" + request.requestedBy(),
                "reason=" + (reason == null ? "" : reason));
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> approvalResponse(ApprovalDecision decision) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("approved", decision.approved());
        response.put("approverId", blankSafe(decision.approverId()));
        response.put("comment", blankSafe(decision.comment()));
        return response;
    }

    private Map<String, Object> approvalState(String status, Boolean approved, String approverId, String comment) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("required", Boolean.TRUE);
        values.put("status", status);
        values.put("approved", approved);
        values.put("approverId", approverId);
        values.put("comment", comment);
        return values;
    }

    private AccountOperationWorkflowSummary summaryFrom(String workflowId, AgentState state, boolean interrupted) {
        Map<String, Object> request = mapValue(state.get(DomainSeparationWorkflowState.REQUEST));
        Map<String, Object> approval = mapValue(state.get(DomainSeparationWorkflowState.APPROVAL));
        Map<String, Object> preparation = mapValue(state.get(DomainSeparationWorkflowState.PREPARATION));
        Map<String, Object> execution = mapValue(state.get(DomainSeparationWorkflowState.EXECUTION));

        String status = deriveStatus(
                stringValue(state.get(DomainSeparationWorkflowState.STATUS)),
                approval,
                preparation,
                execution,
                interrupted);

        return new AccountOperationWorkflowSummary(
                workflowId,
                status,
                objectMapper.convertValue(request.get("operationType"), com.mahitotsu.arachne.samples.domainseparation.domain.AccountOperationType.class),
                stringValue(request.get("accountId")),
                approval == null ? null : objectMapper.convertValue(approval, AccountWorkflowApproval.class),
                preparation == null ? null : objectMapper.convertValue(preparation, AccountOperationPreparation.class),
                execution == null ? null : objectMapper.convertValue(execution, AccountOperationExecution.class));
    }

    private String deriveStatus(
            String storedStatus,
            Map<String, Object> approval,
            Map<String, Object> preparation,
            Map<String, Object> execution,
            boolean interrupted) {
        if (execution != null) {
            return "UNLOCKED".equals(stringValue(execution.get("outcome"))) ? "COMPLETED" : "FAILED";
        }

        String approvalStatus = approval == null ? null : stringValue(approval.get("status"));
        if ("REJECTED".equals(approvalStatus)) {
            return "REJECTED";
        }

        String preparedStatus = preparation == null ? null : stringValue(preparation.get("preparedStatus"));
        if (preparedStatus != null && !isUnlockReady(preparedStatus)) {
            return "FAILED";
        }

        if (interrupted || "PENDING".equals(approvalStatus)) {
            return "PENDING_APPROVAL";
        }

        if (storedStatus == null || storedStatus.isBlank()) {
            return "RUNNING";
        }
        return storedStatus;
    }

    private boolean isUnlockReady(String preparedStatus) {
        return "LOCKED".equals(preparedStatus) || "READY".equals(preparedStatus);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> copy.put(String.valueOf(key), nestedValue));
            return copy;
        }
        return objectMapper.convertValue(value, Map.class);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private <T> T withAuthorization(OperatorAuthorizationContext authorizationContext, WorkflowCall<T> workflowCall) {
        authorizationContextHolder.setCurrent(authorizationContext);
        try {
            return workflowCall.run();
        } finally {
            authorizationContextHolder.clear();
        }
    }

    @FunctionalInterface
    private interface WorkflowCall<T> {
        T run();
    }
}