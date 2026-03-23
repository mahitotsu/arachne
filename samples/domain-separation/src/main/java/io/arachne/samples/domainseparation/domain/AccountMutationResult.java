package io.arachne.samples.domainseparation.domain;

public record AccountMutationResult(
        String accountId,
        String outcome,
        String auditMessage,
        String observedOperatorId) {
}