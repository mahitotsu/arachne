package io.arachne.samples.marketplace.notificationservice;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
class NotificationController {

    private final NotificationApplicationService applicationService;

    NotificationController(NotificationApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/case-outcome")
    NotificationContracts.NotificationDispatchResult caseOutcome(@RequestBody NotificationContracts.NotificationDispatchCommand command) {
        return applicationService.dispatchCaseOutcome(command);
    }
}