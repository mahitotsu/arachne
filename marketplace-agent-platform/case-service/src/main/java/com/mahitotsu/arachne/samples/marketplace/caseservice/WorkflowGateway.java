package com.mahitotsu.arachne.samples.marketplace.caseservice;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.mahitotsu.arachne.samples.marketplace.caseservice.WorkflowContracts.ContinueWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.caseservice.WorkflowContracts.ResumeWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.caseservice.WorkflowContracts.StartWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.caseservice.WorkflowContracts.WorkflowProgressUpdate;
import com.mahitotsu.arachne.samples.marketplace.caseservice.WorkflowContracts.WorkflowResumeResult;
import com.mahitotsu.arachne.samples.marketplace.caseservice.WorkflowContracts.WorkflowStartResult;

interface WorkflowGateway {

    WorkflowStartResult start(StartWorkflowCommand command);

    WorkflowProgressUpdate continueWorkflow(ContinueWorkflowCommand command);

    WorkflowResumeResult resume(ResumeWorkflowCommand command);
}

@Component
class HttpWorkflowGateway implements WorkflowGateway {

    private final RestClient workflowServiceRestClient;

    HttpWorkflowGateway(RestClient workflowServiceRestClient) {
        this.workflowServiceRestClient = workflowServiceRestClient;
    }

    @Override
    public WorkflowStartResult start(StartWorkflowCommand command) {
        return requireBody(workflowServiceRestClient.post()
                .uri("/internal/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .body(command)
                .retrieve()
                .body(WorkflowStartResult.class));
    }

    @Override
    public WorkflowProgressUpdate continueWorkflow(ContinueWorkflowCommand command) {
        return requireBody(workflowServiceRestClient.post()
                .uri("/internal/workflows/{caseId}/messages", command.caseId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ContinueWorkflowRequest(command.message(), command.operatorId(), command.operatorRole(), command.requestedAt()))
                .retrieve()
                .body(WorkflowProgressUpdate.class));
    }

    @Override
    public WorkflowResumeResult resume(ResumeWorkflowCommand command) {
        return requireBody(workflowServiceRestClient.post()
                .uri("/internal/workflows/{caseId}/approvals", command.caseId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResumeWorkflowRequest(command.decision(), command.comment(), command.actorId(), command.actorRole(), command.requestedAt()))
                .retrieve()
                .body(WorkflowResumeResult.class));
    }

    private <T> T requireBody(T body) {
        if (body == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "workflow-service returned an empty response body");
        }
        return body;
    }

    private record ContinueWorkflowRequest(
            String message,
            String operatorId,
            String operatorRole,
            java.time.OffsetDateTime requestedAt) {
    }

    private record ResumeWorkflowRequest(
            String decision,
            String comment,
            String actorId,
            String actorRole,
            java.time.OffsetDateTime requestedAt) {
    }
}