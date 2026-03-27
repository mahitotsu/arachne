package io.arachne.samples.statefulbackendoperations;

public record OperationRecord(
        String operationKey,
        String accountId,
        String targetStatus,
        String reason,
        String executionState,
        String finalStatus,
        String toolUseId,
        String auditMessage) {
}