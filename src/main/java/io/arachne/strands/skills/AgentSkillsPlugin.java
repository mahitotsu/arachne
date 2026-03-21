package io.arachne.strands.skills;

import java.util.List;
import java.util.Objects;

import io.arachne.strands.hooks.BeforeModelCallEvent;
import io.arachne.strands.hooks.HookRegistrar;
import io.arachne.strands.hooks.Plugin;

/**
 * Injects active skill instructions into the system prompt before each model call.
 */
public final class AgentSkillsPlugin implements Plugin {

    private final List<Skill> skills;

    public AgentSkillsPlugin(Skill... skills) {
        this(List.of(skills));
    }

    public AgentSkillsPlugin(List<? extends Skill> skills) {
        Objects.requireNonNull(skills, "skills must not be null");
        this.skills = List.copyOf(skills);
    }

    public List<Skill> availableSkills() {
        return skills;
    }

    @Override
    public void registerHooks(HookRegistrar registrar) {
        if (!skills.isEmpty()) {
            registrar.beforeModelCall(this::injectSkills);
        }
    }

    private void injectSkills(BeforeModelCallEvent event) {
        String injectedBlock = renderSkillBlock();
        if (injectedBlock.isBlank()) {
            return;
        }

        String currentPrompt = event.systemPrompt();
        if (currentPrompt == null || currentPrompt.isBlank()) {
            event.setSystemPrompt(injectedBlock);
            return;
        }
        event.setSystemPrompt(currentPrompt + "\n\n" + injectedBlock);
    }

    private String renderSkillBlock() {
        if (skills.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("The following skills are active for this agent. Apply the skill instructions whenever they are relevant to the user's task.")
                .append("\n\n<active_skills>");

        for (Skill skill : skills) {
            builder.append("\n<skill>")
                    .append("\n<name>").append(escape(skill.name())).append("</name>")
                    .append("\n<description>").append(escape(skill.description())).append("</description>");
            if (skill.location() != null) {
                builder.append("\n<location>").append(escape(skill.location())).append("</location>");
            }
            if (!skill.allowedTools().isEmpty()) {
                builder.append("\n<allowed_tools>");
                for (String allowedTool : skill.allowedTools()) {
                    builder.append("\n<tool>").append(escape(allowedTool)).append("</tool>");
                }
                builder.append("\n</allowed_tools>");
            }
            if (skill.compatibility() != null) {
                builder.append("\n<compatibility>").append(escape(skill.compatibility())).append("</compatibility>");
            }
            if (skill.license() != null) {
                builder.append("\n<license>").append(escape(skill.license())).append("</license>");
            }
            builder.append("\n<instructions><![CDATA[")
                    .append(escapeCdata(skill.instructions()))
                    .append("]]></instructions>")
                    .append("\n</skill>");
        }

        builder.append("\n</active_skills>");
        return builder.toString();
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeCdata(String value) {
        return value.replace("]]>", "]]]]><![CDATA[>");
    }
}