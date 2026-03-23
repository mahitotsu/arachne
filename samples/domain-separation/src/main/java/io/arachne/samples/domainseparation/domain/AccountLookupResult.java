package io.arachne.samples.domainseparation.domain;

public record AccountLookupResult(
        String accountId,
        String currentStatus,
        String observedOperatorId) {
}