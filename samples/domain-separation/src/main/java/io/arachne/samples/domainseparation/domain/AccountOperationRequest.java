package io.arachne.samples.domainseparation.domain;

public record AccountOperationRequest(
        AccountOperationType operationType,
        String accountId,
        String requestedBy) {
}