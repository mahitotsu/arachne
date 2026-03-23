package io.arachne.strands.skills;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolResult;

final class SkillResourceTool implements Tool {

    static final String TOOL_NAME = "read_skill_resource";
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final Map<String, Skill> skillsByName;
    private final ResourceLoader resourceLoader;
    private final ToolSpec spec;

    SkillResourceTool(List<? extends Skill> skills) {
        this(skills, new DefaultResourceLoader());
    }

    SkillResourceTool(List<? extends Skill> skills, ResourceLoader resourceLoader) {
        Objects.requireNonNull(skills, "skills must not be null");
        LinkedHashMap<String, Skill> ordered = new LinkedHashMap<>();
        for (Skill skill : skills) {
            ordered.put(skill.name(), skill);
        }
        this.skillsByName = Map.copyOf(ordered);
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        this.spec = new ToolSpec(
                TOOL_NAME,
                "Reads the content of a listed script, reference, or asset for a skill using the exact skill name and relative path.",
                inputSchema());
    }

    @Override
    public ToolSpec spec() {
        return spec;
    }

    @Override
    public ToolResult invoke(Object input) {
        ReadRequest request = extractRequest(input);
        if (request == null) {
            return ToolResult.error(null, "Skill resource reading requires non-blank 'name' and 'path' fields.");
        }

        Skill skill = skillsByName.get(request.skillName());
        if (skill == null) {
            return ToolResult.error(null, "Unknown skill: " + request.skillName());
        }
        if (!skill.resourceFiles().contains(request.resourcePath())) {
            return ToolResult.error(null, "Unknown skill resource: " + request.resourcePath());
        }

        Resource skillDocument = resolveSkillDocument(skill.location());
        if (skillDocument == null || !skillDocument.exists()) {
            return ToolResult.error(null, "Skill resource location is not readable for skill: " + skill.name());
        }

        try {
            String resourcePath = Objects.requireNonNull(request.resourcePath());
            Resource resource = skillDocument.createRelative(resourcePath);
            if (!resource.exists() || !resource.isReadable()) {
                return ToolResult.error(null, "Skill resource is not readable: " + resourcePath);
            }

            byte[] bytes = readBytes(resource);
            return ToolResult.success(null, resourcePayload(skill.name(), resourcePath, resource, bytes));
        } catch (IOException e) {
            return ToolResult.error(null, "Failed to read skill resource: " + request.resourcePath());
        }
    }

    private ReadRequest extractRequest(Object input) {
        if (!(input instanceof Map<?, ?> map)) {
            return null;
        }
        Object rawName = map.get("name");
        Object rawPath = map.get("path");
        if (!(rawName instanceof String skillName) || skillName.isBlank()) {
            return null;
        }
        if (!(rawPath instanceof String resourcePath) || resourcePath.isBlank()) {
            return null;
        }
        return new ReadRequest(skillName, resourcePath.trim().replace('\\', '/'));
    }

    private Resource resolveSkillDocument(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        if (!location.contains(":")) {
            return new FileSystemResource(location);
        }
        return resourceLoader.getResource(location);
    }

    private byte[] readBytes(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private Map<String, Object> resourcePayload(String skillName, String path, Resource resource, byte[] bytes) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "skill_resource");
        payload.put("name", skillName);
        payload.put("path", path);
        payload.put("mediaType", mediaType(resource, path));
        payload.put("size", bytes.length);
        if (isUtf8Text(bytes)) {
            payload.put("encoding", "utf-8");
            payload.put("content", new String(bytes, StandardCharsets.UTF_8));
        } else {
            payload.put("encoding", "base64");
            payload.put("content", Base64.getEncoder().encodeToString(bytes));
        }
        return Map.copyOf(payload);
    }

    private String mediaType(Resource resource, String path) {
        String fileName = resource.getFilename();
        if (fileName == null) {
            fileName = path;
        }
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".log")) {
            return "text/plain";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return "application/yaml";
        }
        if (lower.endsWith(".sh")) {
            return "text/x-shellscript";
        }
        return "application/octet-stream";
    }

    private boolean isUtf8Text(byte[] bytes) {
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            return false;
        }

        for (byte value : bytes) {
            if ((value & 0xFF) == 0) {
                return false;
            }
        }
        return true;
    }

    private static ObjectNode inputSchema() {
        ObjectNode root = JSON.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "Exact skill name from the available skill catalog.");
        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "Exact relative resource path from the skill activation payload, such as references/release-template.md.");
        root.putArray("required").add("name").add("path");
        root.put("additionalProperties", false);
        return root;
    }

    private record ReadRequest(String skillName, String resourcePath) {
    }
}