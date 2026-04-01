package com.mahitotsu.arachne.samples.marketplace.notificationservice;

final class NotificationContracts {

    private NotificationContracts() {
    }

    record NotificationDispatchCommand(
            String caseId,
            String outcomeType,
            String outcomeStatus,
            String settlementReference) {
    }

    record NotificationDispatchResult(
            String dispatchStatus,
            String deliveryStatus,
            String summary) {
    }
}