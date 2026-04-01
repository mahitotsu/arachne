package com.mahitotsu.arachne.strands.skills;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Parses AgentSkills.io {@code SKILL.md} documents.
 */
public final class SkillParser {

    private static final Pattern FRONTMATTER_LINE = Pattern.compile("^(\\s*[A-Za-z0-9_-]+):(\\s+)(.+)$");

    public Skill parse(String markdown) {
        return parse(markdown, null);
    }

    public Skill parse(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            return parse(Files.readString(path, StandardCharsets.UTF_8), path.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read skill file: " + path, e);
        }
    }

    public Skill parse(Resource resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        try (InputStream inputStream = resource.getInputStream()) {
            return parse(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8), resolveLocation(resource));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read skill resource: " + resource.getDescription(), e);
        }
    }

    private Skill parse(String markdown, String location) {
        ParsedFrontmatter parsed = splitFrontmatter(markdown);
        Map<String, Object> frontmatter = parseFrontmatter(parsed.frontmatter());
        String name = requireText(frontmatter, "name");
        String description = requireText(frontmatter, "description");
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(parseMetadata(frontmatter.get("metadata")));
        Object rawAllowedTools = frontmatter.containsKey("allowed-tools")
                ? frontmatter.get("allowed-tools")
                : metadata.remove("allowed-tools");
        List<String> allowedTools = parseAllowedTools(rawAllowedTools);
        String compatibility = asOptionalText(frontmatter.get("compatibility"));
        String license = asOptionalText(frontmatter.get("license"));
        return new Skill(name, description, parsed.body(), allowedTools, metadata, compatibility, license, location);
    }

    private ParsedFrontmatter splitFrontmatter(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new SkillParseException("SKILL.md must not be empty");
        }

        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }

        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !lines[0].trim().equals("---")) {
            throw new SkillParseException("SKILL.md must start with a YAML frontmatter block");
        }

        int closingLine = -1;
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].trim().equals("---")) {
                closingLine = index;
                break;
            }
        }
        if (closingLine < 0) {
            throw new SkillParseException("SKILL.md frontmatter is missing the closing --- delimiter");
        }

        String frontmatter = String.join("\n", java.util.Arrays.copyOfRange(lines, 1, closingLine));
        String body = String.join("\n", java.util.Arrays.copyOfRange(lines, closingLine + 1, lines.length)).trim();
        return new ParsedFrontmatter(frontmatter, body);
    }

    private Map<String, Object> parseFrontmatter(String frontmatter) {
        Object loaded = loadYaml(frontmatter);
        if (loaded == null) {
            return Map.of();
        }
        if (!(loaded instanceof Map<?, ?> rawMap)) {
            throw new SkillParseException("SKILL.md frontmatter must be a YAML mapping");
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), (Object) normalizeYamlValue(entry.getValue()));
        }
        return normalized;
    }

    private Object loadYaml(String frontmatter) {
        Yaml yaml = new Yaml();
        try {
            return yaml.load(frontmatter);
        } catch (YAMLException original) {
            try {
                return yaml.load(fixYamlColons(frontmatter));
            } catch (YAMLException fallback) {
                throw new SkillParseException("SKILL.md frontmatter contains invalid YAML", original);
            }
        }
    }

    private Object normalizeYamlValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalizeYamlValue(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof List<?> listValue) {
            List<Object> normalized = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                normalized.add(normalizeYamlValue(item));
            }
            return normalized;
        }
        return value;
    }

    private String requireText(Map<String, Object> frontmatter, String key) {
        String value = asOptionalText(frontmatter.get(key));
        if (value == null) {
            throw new SkillParseException("SKILL.md frontmatter requires a non-empty '" + key + "' field");
        }
        return value;
    }

    private String asOptionalText(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = String.valueOf(rawValue).trim();
        return value.isEmpty() ? null : value;
    }

    private Map<String, Object> parseMetadata(Object rawMetadata) {
        if (rawMetadata == null) {
            return Map.of();
        }
        if (!(rawMetadata instanceof Map<?, ?> metadataMap)) {
            throw new SkillParseException("SKILL.md 'metadata' must be a YAML mapping");
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : metadataMap.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), (Object) normalizeYamlValue(entry.getValue()));
        }
        return normalized;
    }

    private List<String> parseAllowedTools(Object rawAllowedTools) {
        if (rawAllowedTools == null) {
            return List.of();
        }
        if (rawAllowedTools instanceof String allowedToolsText) {
            String trimmed = allowedToolsText.trim();
            return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
        }
        if (rawAllowedTools instanceof List<?> allowedToolsList) {
            List<String> normalized = new ArrayList<>(allowedToolsList.size());
            for (Object item : allowedToolsList) {
                if (item == null) {
                    continue;
                }
                String value = String.valueOf(item).trim();
                if (!value.isEmpty()) {
                    normalized.add(value);
                }
            }
            return List.copyOf(normalized);
        }
        throw new SkillParseException("SKILL.md 'allowed-tools' must be a string or YAML list");
    }

    private String resolveLocation(Resource resource) {
        try {
            return resource.getURI().toString();
        } catch (IOException ignored) {
            return resource.getDescription();
        }
    }

    private String fixYamlColons(String frontmatter) {
        StringBuilder fixed = new StringBuilder();
        String[] lines = frontmatter.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            Matcher matcher = FRONTMATTER_LINE.matcher(line);
            if (matcher.matches()) {
                String value = matcher.group(3);
                if (value.contains(":") && !isQuoted(value) && !value.startsWith("|") && !value.startsWith(">")) {
                    String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
                    line = matcher.group(1) + ":" + matcher.group(2) + "\"" + escaped + "\"";
                }
            }
            if (index > 0) {
                fixed.append('\n');
            }
            fixed.append(line);
        }
        return fixed.toString();
    }

    private boolean isQuoted(String value) {
        return value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")));
    }

    private record ParsedFrontmatter(String frontmatter, String body) {
    }
}