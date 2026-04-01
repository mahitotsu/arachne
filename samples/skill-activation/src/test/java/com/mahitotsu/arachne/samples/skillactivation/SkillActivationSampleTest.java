package com.mahitotsu.arachne.samples.skillactivation;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
class SkillActivationSampleTest {

    @Autowired
    private DemoSkillsModel demoSkillsModel;

    @Test
    void sampleBootsAndPrintsExpectedSkillActivationAndRestoreFlow(CapturedOutput output) {
        assertThat(output)
                .contains("Arachne skill activation sample")
            .contains("tools>")
            .contains("activate_skill")
            .contains("read_skill_resource")
                .contains("first.request> Prepare today's release.")
                .contains("first.reply> Loaded release-checklist.")
                .contains("state.loadedSkills> [release-checklist]")
                .contains("first.resourceReads> [skill_activation, skill_resource]")
                .contains("first.referencePath> references/release-template.md")
                .contains("second.request> What should I do next?")
                .contains("second.reply> Reusing loaded release-checklist.")
                .contains("restored.loadedSkills> [release-checklist]")
                .contains("prompt.catalogPresent> true")
                .contains("prompt.activeSkillPresentAfterRestore> true")
                .contains("prompt.resourceListPresentAfterRestore> true");

        assertThat(demoSkillsModel.systemPrompts()).isNotEmpty();
        assertThat(demoSkillsModel.systemPrompts().getFirst()).contains("<available_skills>");
        assertThat(demoSkillsModel.systemPrompts().getLast())
                .contains("<active_skills>")
                .contains("<resources>");
    }
}