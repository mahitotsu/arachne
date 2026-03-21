package io.arachne.strands.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Parsed AgentSkills.io skill definition.
 */
public record Skill(
        String name,
        String description,
        String instructions,
        List<String> allowedTools,
        Map<String, Object> metadata,
        String compatibility,
        String license,
        String location) {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$");

    public Skill {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("skill name must not be blank");
        }
        if (name.length() > 64) {
            throw new IllegalArgumentException("skill name must be 64 characters or fewer");
        }
        if (!NAME_PATTERN.matcher(name).matches() || name.contains("--")) {
            throw new IllegalArgumentException("skill name must use lowercase letters, digits, and hyphens");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("skill description must not be blank");
        }

        instructions = instructions == null ? "" : instructions.trim();
        allowedTools = copyAllowedTools(allowedTools);
        metadata = copyMetadata(metadata);
        compatibility = normalizeBlank(compatibility);
        license = normalizeBlank(license);
        location = normalizeBlank(location);
    }

    public Skill(String name, String description, String instructions) {
        this(name, description, instructions, List.of(), Map.of(), null, null, null);
    }

    private static List<String> copyAllowedTools(List<String> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return List.of();
        }
        List<String> copied = new ArrayList<>(allowedTools.size());
        for (String allowedTool : allowedTools) {
            if (allowedTool == null || allowedTool.isBlank()) {
                continue;
            }
            copied.add(allowedTool);
        }
        return List.copyOf(copied);
    }

    private static Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
        metadata.forEach((key, value) -> copied.put(Objects.requireNonNull(key, "metadata key must not be null"), copyValue(value)));
        return Collections.unmodifiableMap(copied);
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                copied.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copied);
        }
        if (value instanceof List<?> listValue) {
            List<Object> copied = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                copied.add(copyValue(item));
            }
            return List.copyOf(copied);
        }
        return value;
    }

    private static String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}