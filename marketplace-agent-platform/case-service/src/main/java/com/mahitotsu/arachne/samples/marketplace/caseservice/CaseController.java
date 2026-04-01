package com.mahitotsu.arachne.samples.marketplace.caseservice;

import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.AddCaseMessageCommand;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.ApprovalSubmissionResult;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.CaseDetailView;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.CaseListItem;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.CreateCaseCommand;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.SubmitApprovalCommand;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/cases")
class CaseController {

    private final CaseApplicationService applicationService;
    private final CaseProjectionRepository repository;
    private final CaseActivityStreamRegistry activityStreamRegistry;

    CaseController(
            CaseApplicationService applicationService,
            CaseProjectionRepository repository,
            CaseActivityStreamRegistry activityStreamRegistry) {
        this.applicationService = applicationService;
        this.repository = repository;
        this.activityStreamRegistry = activityStreamRegistry;
    }

    @PostMapping
    CaseDetailView createCase(@RequestBody CreateCaseCommand command) {
        return applicationService.createCase(command);
    }

    @GetMapping
    List<CaseListItem> listCases(
            @RequestParam(name = "q", required = false) String searchText,
            @RequestParam(name = "caseType", required = false) String caseType,
            @RequestParam(name = "caseStatus", required = false) String caseStatus) {
        return applicationService.listCases(searchText, caseType, caseStatus);
    }

    @GetMapping("/{caseId}")
    CaseDetailView getCase(@PathVariable String caseId) {
        return applicationService.getCase(caseId);
    }

    @PostMapping("/{caseId}/messages")
    CaseDetailView addMessage(@PathVariable String caseId, @RequestBody AddCaseMessageCommand command) {
        return applicationService.addMessage(caseId, command);
    }

    @GetMapping(path = "/{caseId}/activity-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter activityStream(@PathVariable String caseId) {
        if (!repository.exists(caseId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Case not found: " + caseId);
        }
        return activityStreamRegistry.register(caseId);
    }

    @PostMapping("/{caseId}/approvals")
    ApprovalSubmissionResult submitApproval(@PathVariable String caseId, @RequestBody SubmitApprovalCommand command) {
        return applicationService.submitApproval(caseId, command);
    }
}