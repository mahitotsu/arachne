package com.mahitotsu.arachne.samples.marketplace.notificationservice;

import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;
import org.springframework.stereotype.Service;

@Service
class NotificationApplicationService {

    private final NotificationRepository repository;
    private final AgentFactory agentFactory;
    private final Tool notificationTemplateLookupTool;

    NotificationApplicationService(
            NotificationRepository repository,
            AgentFactory agentFactory,
            Tool notificationTemplateLookupTool) {
        this.repository = repository;
        this.agentFactory = agentFactory;
        this.notificationTemplateLookupTool = notificationTemplateLookupTool;
    }

    NotificationContracts.NotificationDispatchResult dispatchCaseOutcome(NotificationContracts.NotificationDispatchCommand command) {
        var composition = normalizeComposition(command, composeCaseOutcome(command));
        var record = repository.recordDispatch(command, composition);
        return new NotificationContracts.NotificationDispatchResult(
                record.dispatchStatus(),
                record.deliveryStatus(),
                record.summary());
    }

    private NotificationContracts.NotificationComposition composeCaseOutcome(NotificationContracts.NotificationDispatchCommand command) {
        return agentFactory.builder("notification-agent")
                .tools(notificationTemplateLookupTool)
                .build()
                .run(caseOutcomePrompt(command), NotificationContracts.NotificationComposition.class);
    }

    private NotificationContracts.NotificationComposition normalizeComposition(
            NotificationContracts.NotificationDispatchCommand command,
            NotificationContracts.NotificationComposition composition) {
        var defaults = defaultComposition(command);
        if (composition == null) {
            return defaults;
        }
        return new NotificationContracts.NotificationComposition(
                firstNonBlank(composition.participantChannel(), defaults.participantChannel()),
                firstNonBlank(composition.operatorChannel(), defaults.operatorChannel()),
                firstNonBlank(composition.participantSummary(), defaults.participantSummary()),
                firstNonBlank(composition.operatorSummary(), defaults.operatorSummary()),
                firstNonBlank(composition.summary(), defaults.summary()));
    }

    private NotificationContracts.NotificationComposition defaultComposition(NotificationContracts.NotificationDispatchCommand command) {
        String settlementReference = blankSafe(command.settlementReference());
        String participantSummary;
        String operatorSummary;
        if ("REFUND_EXECUTED".equalsIgnoreCase(command.outcomeType())) {
            participantSummary = "Participant notification prepared for refund settlement reference " + settlementReference + ".";
            operatorSummary = "Operator notification prepared for refund settlement reference " + settlementReference + ".";
        }
        else if ("CONTINUED_HOLD_RECORDED".equalsIgnoreCase(command.outcomeType())) {
            participantSummary = "Participant notification prepared for continued hold settlement reference " + settlementReference + ".";
            operatorSummary = "Operator notification prepared for continued hold settlement reference " + settlementReference + ".";
        }
        else {
            participantSummary = "Participant notification prepared for settlement reference " + settlementReference + ".";
            operatorSummary = "Operator notification prepared for settlement reference " + settlementReference + ".";
        }
        return new NotificationContracts.NotificationComposition(
                "EMAIL",
                "INTERNAL_DASHBOARD",
                participantSummary,
                operatorSummary,
                "Notification service queued participant and operator notifications for settlement reference " + settlementReference + ".");
    }

    private String caseOutcomePrompt(NotificationContracts.NotificationDispatchCommand command) {
        return String.join("\n",
                "mode=case-outcome",
                "caseId=" + blankSafe(command.caseId()),
                "outcomeType=" + blankSafe(command.outcomeType()),
                "outcomeStatus=" + blankSafe(command.outcomeStatus()),
                "settlementReference=" + blankSafe(command.settlementReference()),
                "instructions=Call notification_template_lookup exactly once, then respond through structured_output with participantChannel, operatorChannel, participantSummary, operatorSummary, and summary. Keep the composition concise and leave durable dispatch status ownership to notification-service.");
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }
}