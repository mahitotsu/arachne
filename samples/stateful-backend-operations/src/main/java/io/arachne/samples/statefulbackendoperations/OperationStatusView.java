package io.arachne.samples.statefulbackendoperations;

public record OperationStatusView(
        String operationKey,
        String accountId,
        String executionState,
        String targetStatus,
        String finalStatus) {
}