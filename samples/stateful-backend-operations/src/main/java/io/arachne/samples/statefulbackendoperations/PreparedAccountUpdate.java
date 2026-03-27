package io.arachne.samples.statefulbackendoperations;

public record PreparedAccountUpdate(
        String operationKey,
        String accountId,
        String currentStatus,
        String targetStatus,
        String executionState) {
}