package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.ContinueWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.ResumeWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.StartWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowProgressUpdate;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowResumeResult;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowStartResult;
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