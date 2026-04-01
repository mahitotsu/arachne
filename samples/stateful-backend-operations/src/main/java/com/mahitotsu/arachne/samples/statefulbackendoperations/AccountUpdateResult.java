package com.mahitotsu.arachne.samples.statefulbackendoperations;

public record AccountUpdateResult(
        String operationKey,
        String accountId,
        String finalStatus,
        String outcome,
        boolean replayed,
        String auditMessage) {
}