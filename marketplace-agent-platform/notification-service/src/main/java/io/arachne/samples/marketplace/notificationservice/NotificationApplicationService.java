package io.arachne.samples.marketplace.notificationservice;

import org.springframework.stereotype.Service;

@Service
class NotificationApplicationService {

    private final NotificationRepository repository;

    NotificationApplicationService(NotificationRepository repository) {
        this.repository = repository;
    }

    NotificationContracts.NotificationDispatchResult dispatchCaseOutcome(NotificationContracts.NotificationDispatchCommand command) {
        var record = repository.recordDispatch(command);
        return new NotificationContracts.NotificationDispatchResult(
                record.dispatchStatus(),
                record.deliveryStatus(),
                record.summary());
    }
}