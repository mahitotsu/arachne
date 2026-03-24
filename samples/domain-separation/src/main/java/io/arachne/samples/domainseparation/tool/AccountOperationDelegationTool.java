package io.arachne.samples.domainseparation.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import io.arachne.samples.domainseparation.domain.AccountOperationExecution;
import io.arachne.samples.domainseparation.domain.AccountOperationPreparation;
import io.arachne.samples.domainseparation.domain.AccountOperationType;
import io.arachne.strands.agent.Agent;
import io.arachne.strands.spring.AgentFactory;
import io.arachne.strands.tool.annotation.StrandsTool;
import io.arachne.strands.tool.annotation.ToolParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Service
@Validated
public class AccountOperationDelegationTool {

    private static final Logger log = LoggerFactory.getLogger(AccountOperationDelegationTool.class);

    private final ObjectProvider<AgentFactory> agentFactoryProvider;

    public AccountOperationDelegationTool(ObjectProvider<AgentFactory> agentFactoryProvider) {
        this.agentFactoryProvider = agentFactoryProvider;
    }

    @StrandsTool(
            name = "prepare_account_operation",
            description = "Delegate to the operations executor to inspect the requested account operation.",
            qualifiers = "operations-coordinator")
    public AccountOperationPreparation prepareAccountOperation(
            @ToolParam(name = "operationType", description = "Requested account operation type") @NotNull
            AccountOperationType operationType,
            @ToolParam(name = "accountId", description = "Target account identifier") @NotBlank
            String accountId,
            @ToolParam(name = "requestedBy", description = "Operator requesting the action") @NotBlank
            String requestedBy,
            @ToolParam(name = "reason", description = "Business reason for the operation", required = false)
            String reason) {
        log.info("demo.trace> delegating prepare request to operations-executor for {} {}", operationType, accountId);
        Agent executorAgent = agentFactory().builder("operations-executor").build();
        String prompt = executorPrompt("prepare", operationType, accountId, requestedBy, reason);
        logExecutorPrompt("prepare", prompt);
        AccountOperationPreparation result = executorAgent.run(prompt, AccountOperationPreparation.class);
        log.info(
                "demo.trace> executor prepare response phase={} preparedStatus={} authorizedOperatorId={}",
                result.phase(),
                result.preparedStatus(),
                result.authorizedOperatorId());
        return result;
    }

    @StrandsTool(
            name = "execute_account_operation",
            description = "Delegate to the operations executor to apply the requested account operation.",
            qualifiers = "operations-coordinator")
    public AccountOperationExecution executeAccountOperation(
            @ToolParam(name = "operationType", description = "Requested account operation type") @NotNull
            AccountOperationType operationType,
            @ToolParam(name = "accountId", description = "Target account identifier") @NotBlank
            String accountId,
            @ToolParam(name = "requestedBy", description = "Operator requesting the action") @NotBlank
            String requestedBy,
            @ToolParam(name = "reason", description = "Business reason for the operation", required = false)
            String reason) {
        log.info("demo.trace> delegating execution request to operations-executor for {} {}", operationType, accountId);
        Agent executorAgent = agentFactory().builder("operations-executor").build();
        String prompt = executorPrompt("execute", operationType, accountId, requestedBy, reason);
        logExecutorPrompt("execute", prompt);
        AccountOperationExecution result = executorAgent.run(prompt, AccountOperationExecution.class);
        log.info(
                "demo.trace> executor execute response phase={} outcome={} authorizedOperatorId={}",
                result.phase(),
                result.outcome(),
                result.authorizedOperatorId());
        return result;
    }

    private AgentFactory agentFactory() {
        return agentFactoryProvider.getObject();
    }

    private String executorPrompt(
            String mode,
            AccountOperationType operationType,
            String accountId,
            String requestedBy,
            String reason) {
        return String.join("\n",
                "mode=" + mode,
                "operationType=" + operationType,
                "accountId=" + accountId,
                "requestedBy=" + requestedBy,
                "reason=" + (reason == null ? "" : reason));
    }

    private void logExecutorPrompt(String phase, String prompt) {
        log.info("demo.trace> executor {} prompt begin", phase);
        for (String line : prompt.split("\\R", -1)) {
            log.info("demo.trace> executor prompt | {}", line);
        }
        log.info("demo.trace> executor {} prompt end", phase);
    }
}