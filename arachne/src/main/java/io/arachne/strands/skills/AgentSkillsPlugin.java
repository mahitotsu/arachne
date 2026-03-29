package io.arachne.strands.skills;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.arachne.strands.agent.AgentState;
import io.arachne.strands.hooks.AfterToolCallEvent;
import io.arachne.strands.hooks.BeforeModelCallEvent;
import io.arachne.strands.hooks.BeforeToolCallEvent;
import io.arachne.strands.hooks.HookRegistrar;
import io.arachne.strands.hooks.Plugin;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolResult;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;

/**
 * Exposes delayed skill activation through a compact catalog prompt and a dedicated activation tool.
 */
public final class AgentSkillsPlugin implements Plugin {

    private static final String LOADED_SKILLS_STATE_KEY = "arachne.skills.loaded";

    private final List<Skill> skills;
    private final Map<String, Skill> skillsByName;
    private final Tool activationTool;
    private final Tool resourceTool;

    public AgentSkillsPlugin(Skill... skills) {
        this(List.of(skills));
    }

    public AgentSkillsPlugin(List<? extends Skill> skills) {
        Objects.requireNonNull(skills, "skills must not be null");
        this.skills = List.copyOf(skills);
        LinkedHashMap<String, Skill> ordered = new LinkedHashMap<>();
        for (Skill skill : skills) {
            ordered.put(skill.name(), skill);
        }
        this.skillsByName = Map.copyOf(ordered);
        this.activationTool = new SkillActivationTool(this.skills);
        this.resourceTool = new SkillResourceTool(this.skills);
    }

    public List<Skill> availableSkills() {
        return skills;
    }

    @Override
    public List<Tool> tools() {
        if (skills.isEmpty()) {
            return List.of();
        }
        return List.of(activationTool, resourceTool);
    }

    @Override
    public void registerHooks(HookRegistrar registrar) {
        if (!skills.isEmpty()) {
            registrar.beforeModelCall(this::injectSkills)
                    .beforeToolCall(this::shortCircuitLoadedSkillActivation)
                    .afterToolCall(this::rememberLoadedSkill);
        }
    }

    private void injectSkills(BeforeModelCallEvent event) {
        String injectedBlock = renderInjectedBlock(event);
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

    private void shortCircuitLoadedSkillActivation(BeforeToolCallEvent event) {
        if (!SkillActivationTool.TOOL_NAME.equals(event.toolName())) {
            return;
        }

        String skillName = SkillActivationTool.extractRequestedSkillName(event.input());
        if (skillName == null) {
            return;
        }

        List<String> loadedSkillNames = loadedSkillNames(event.state());
        if (!loadedSkillNames.contains(skillName)) {
            return;
        }

        Skill skill = skillsByName.get(skillName);
        if (skill == null) {
            return;
        }

        event.skipWith(ToolResult.success(
                event.toolUseId(),
                SkillActivationTool.activationPayload(skill, true)));
    }

    private void rememberLoadedSkill(AfterToolCallEvent event) {
        if (!SkillActivationTool.TOOL_NAME.equals(event.toolName())) {
            return;
        }
        if (event.result().status() != ToolResult.ToolStatus.SUCCESS) {
            return;
        }

        String skillName = SkillActivationTool.extractActivatedSkillName(event.result().content());
        if (skillName == null || !skillsByName.containsKey(skillName)) {
            return;
        }

        LinkedHashSet<String> loaded = new LinkedHashSet<>(loadedSkillNames(event.state()));
        loaded.add(skillName);
        event.state().put(LOADED_SKILLS_STATE_KEY, List.copyOf(loaded));
    }

    private String renderInjectedBlock(BeforeModelCallEvent event) {
        if (skills.isEmpty()) {
            return "";
        }

        List<Skill> loadedSkills = resolveLoadedSkills(event.state(), recentlyActivatedSkillNames(event.messages()));
        String catalogBlock = renderCatalogBlock();
        String activeSkillBlock = renderActiveSkillsBlock(loadedSkills);
        if (activeSkillBlock.isBlank()) {
            return catalogBlock;
        }
        return catalogBlock + "\n\n" + activeSkillBlock;
    }

    private List<Skill> resolveLoadedSkills(AgentState state, List<String> excludedNames) {
        LinkedHashSet<String> excluded = new LinkedHashSet<>(excludedNames);
        List<Skill> resolved = new ArrayList<>();
        for (String skillName : loadedSkillNames(state)) {
            if (excluded.contains(skillName)) {
                continue;
            }
            Skill skill = skillsByName.get(skillName);
            if (skill != null) {
                resolved.add(skill);
            }
        }
        return List.copyOf(resolved);
    }

    private List<String> recentlyActivatedSkillNames(List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }

        Message lastMessage = messages.get(messages.size() - 1);
        if (lastMessage.role() != Message.Role.USER) {
            return List.of();
        }

        LinkedHashSet<String> skillNames = new LinkedHashSet<>();
        for (ContentBlock block : lastMessage.content()) {
            if (block instanceof ContentBlock.ToolResult toolResult) {
                String skillName = SkillActivationTool.extractActivatedSkillName(toolResult.content());
                if (skillName != null) {
                    skillNames.add(skillName);
                }
            }
        }
        return List.copyOf(skillNames);
    }

    private List<String> loadedSkillNames(AgentState state) {
        Object rawValue = state.get(LOADED_SKILLS_STATE_KEY);
        if (!(rawValue instanceof List<?> values)) {
            return List.of();
        }

        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Object value : values) {
            if (value instanceof String name && !name.isBlank()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private String renderCatalogBlock() {
        if (skills.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("The following skill catalog is available for this agent. Skills in <available_skills> are not active until you load them with the activate_skill tool using the exact skill name. After activation, use read_skill_resource with the exact relative path only when you need the contents of a listed script, reference, or asset. Load only the skills that are relevant to the user's task, and avoid loading a skill that is already active.")
                .append("\n\n<available_skills>");

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
            builder.append("\n</skill>");
        }

        builder.append("\n</available_skills>");
        return builder.toString();
    }

    private String renderActiveSkillsBlock(List<Skill> activeSkills) {
        if (activeSkills.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("The following skills are already loaded for this conversation. Apply these instructions until the conversation ends. If you need the contents of a listed resource, call read_skill_resource with the exact skill name and resource path.")
                .append("\n\n<active_skills>");

        for (Skill skill : activeSkills) {
            builder.append("\n<skill>")
                    .append("\n<name>").append(escape(skill.name())).append("</name>")
                    .append("\n<description>").append(escape(skill.description())).append("</description>");
            if (!skill.allowedTools().isEmpty()) {
                builder.append("\n<allowed_tools>");
                for (String allowedTool : skill.allowedTools()) {
                    builder.append("\n<tool>").append(escape(allowedTool)).append("</tool>");
                }
                builder.append("\n</allowed_tools>");
            }
            appendResourceBlock(builder, skill.resourceFiles());
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

    private void appendResourceBlock(StringBuilder builder, List<String> resourceFiles) {
        if (resourceFiles.isEmpty()) {
            return;
        }

        builder.append("\n<resources>");
        for (String resourceFile : resourceFiles) {
            builder.append("\n<resource>").append(escape(resourceFile)).append("</resource>");
        }
        builder.append("\n</resources>");
    }
}