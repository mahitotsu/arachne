package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.strands.skills.SkillParser;

class WorkflowServiceConfigurationTest {

    private final WorkflowServiceConfiguration configuration = new WorkflowServiceConfiguration();
    private final SkillParser skillParser = new SkillParser();

    @Test
    void assessmentSkillsExcludeApprovalEscalation() {
        List<String> skillNames = configuration.caseWorkflowAssessmentSkills(skillParser).stream()
                .map(skill -> skill.name())
                .toList();

        assertThat(skillNames)
                .containsExactly("marketplace-dispute-intake", "item-not-received-investigation")
                .doesNotContain("approval-escalation-and-resume");
    }

    @Test
    void approvalSkillsRetainApprovalEscalation() {
        List<String> skillNames = configuration.caseWorkflowApprovalSkills(skillParser).stream()
                .map(skill -> skill.name())
                .toList();

        assertThat(skillNames)
                .containsExactly(
                        "marketplace-dispute-intake",
                        "item-not-received-investigation",
                        "approval-escalation-and-resume");
    }
}