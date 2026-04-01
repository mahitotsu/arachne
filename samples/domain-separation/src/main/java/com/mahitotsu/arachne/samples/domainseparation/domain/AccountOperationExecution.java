package com.mahitotsu.arachne.samples.domainseparation.domain;

public record AccountOperationExecution(
        String phase,
        AccountOperationType operationType,
        String accountId,
        String outcome,
        String auditMessage,
        String authorizedOperatorId) {
}