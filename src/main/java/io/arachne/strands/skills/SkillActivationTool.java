package io.arachne.strands.skills;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolResult;

final class SkillActivationTool implements Tool {

    static final String TOOL_NAME = "activate_skill";
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final Map<String, Skill> skillsByName;
    private final ToolSpec spec;

    SkillActivationTool(List<? extends Skill> skills) {
        Objects.requireNonNull(skills, "skills must not be null");
        LinkedHashMap<String, Skill> ordered = new LinkedHashMap<>();
        for (Skill skill : skills) {
            ordered.put(skill.name(), skill);
        }
        this.skillsByName = Map.copyOf(ordered);
        this.spec = new ToolSpec(
                TOOL_NAME,
                "Loads the full instructions for a named skill from the available skill catalog.",
                inputSchema());
    }

    @Override
    public ToolSpec spec() {
        return spec;
    }

    @Override
    public ToolResult invoke(Object input) {
        String skillName = extractRequestedSkillName(input);
        if (skillName == null) {
            return ToolResult.error(null, "Skill activation requires a non-blank 'name' field.");
        }

        Skill skill = skillsByName.get(skillName);
        if (skill == null) {
            return ToolResult.error(null, "Unknown skill: " + skillName);
        }

        return ToolResult.success(null, activationPayload(skill, false));
    }

    static String extractRequestedSkillName(Object input) {
        if (!(input instanceof Map<?, ?> map)) {
            return null;
        }
        Object rawName = map.get("name");
        if (!(rawName instanceof String skillName) || skillName.isBlank()) {
            return null;
        }
        return skillName;
    }

    static String extractActivatedSkillName(Object content) {
        if (!(content instanceof Map<?, ?> map)) {
            return null;
        }
        Object rawType = map.get("type");
        Object rawName = map.get("name");
        if (!"skill_activation".equals(rawType) || !(rawName instanceof String skillName) || skillName.isBlank()) {
            return null;
        }
        return skillName;
    }

    static Map<String, Object> activationPayload(Skill skill, boolean alreadyLoaded) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "skill_activation");
        payload.put("name", skill.name());
        payload.put("description", skill.description());
        payload.put("instructions", skill.instructions());
        if (!skill.allowedTools().isEmpty()) {
            payload.put("allowedTools", List.copyOf(new LinkedHashSet<>(skill.allowedTools())));
        }
        if (skill.compatibility() != null) {
            payload.put("compatibility", skill.compatibility());
        }
        if (skill.license() != null) {
            payload.put("license", skill.license());
        }
        if (skill.location() != null) {
            payload.put("location", skill.location());
        }
        payload.put("alreadyLoaded", alreadyLoaded);
        return Map.copyOf(payload);
    }

    private static ObjectNode inputSchema() {
        ObjectNode root = JSON.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "Exact skill name from the available skill catalog.");
        ArrayNode required = root.putArray("required");
        required.add("name");
        root.put("additionalProperties", false);
        return root;
    }
}