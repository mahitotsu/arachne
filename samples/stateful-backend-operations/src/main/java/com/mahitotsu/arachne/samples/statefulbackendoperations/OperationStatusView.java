package com.mahitotsu.arachne.samples.statefulbackendoperations;

public record OperationStatusView(
        String operationKey,
        String accountId,
        String executionState,
        String targetStatus,
        String finalStatus) {
}