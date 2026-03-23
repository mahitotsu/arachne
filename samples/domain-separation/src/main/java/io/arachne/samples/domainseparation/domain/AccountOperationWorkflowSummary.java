package io.arachne.samples.domainseparation.domain;

public record AccountOperationWorkflowSummary(
        String workflowId,
        String status,
        AccountOperationType operationType,
        String accountId,
        AccountWorkflowApproval approval,
        AccountOperationPreparation preparation,
        AccountOperationExecution execution) {
}