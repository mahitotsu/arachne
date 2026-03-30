package io.arachne.samples.marketplace.workflowservice;

import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.ContinueWorkflowCommand;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.ResumeWorkflowCommand;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.StartWorkflowCommand;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowProgressUpdate;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowResumeResult;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowStartResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/workflows")
class WorkflowController {

    private final WorkflowApplicationService applicationService;

    WorkflowController(WorkflowApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    WorkflowStartResult start(@RequestBody StartWorkflowCommand command) {
        return applicationService.start(command);
    }

    @PostMapping("/{caseId}/messages")
    WorkflowProgressUpdate continueWorkflow(@PathVariable String caseId, @RequestBody ContinueWorkflowCommand command) {
        return applicationService.continueWorkflow(caseId, command);
    }

    @PostMapping("/{caseId}/approvals")
    WorkflowResumeResult resume(@PathVariable String caseId, @RequestBody ResumeWorkflowCommand command) {
        return applicationService.resume(caseId, command);
    }
}