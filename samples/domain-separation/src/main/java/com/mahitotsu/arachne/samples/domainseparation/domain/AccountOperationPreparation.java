package com.mahitotsu.arachne.samples.domainseparation.domain;

public record AccountOperationPreparation(
        String phase,
        AccountOperationType operationType,
        String accountId,
        String preparedStatus,
        String preparedSummary,
        String authorizedOperatorId) {
}