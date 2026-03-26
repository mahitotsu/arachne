package io.arachne.strands.tool.builtin;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolResult;

public final class ResourceReaderTool implements Tool {

    public static final String TOOL_NAME = "resource_reader";
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ResourcePatternResolver resourcePatternResolver;
    private final BuiltInResourceAccessPolicy accessPolicy;
    private final ToolSpec spec = new ToolSpec(
            TOOL_NAME,
            "Reads a single allowlisted classpath or file resource.",
            inputSchema());

    public ResourceReaderTool(ResourcePatternResolver resourcePatternResolver, BuiltInResourceAccessPolicy accessPolicy) {
        this.resourcePatternResolver = Objects.requireNonNull(resourcePatternResolver, "resourcePatternResolver must not be null");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy must not be null");
    }

    @Override
    public ToolSpec spec() {
        return spec;
    }

    @Override
    public ToolResult invoke(Object input) {
        String location;
        try {
            location = extractLocation(input);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(null, e.getMessage());
        }

        if (!accessPolicy.isAllowed(location)) {
            return ToolResult.error(null, "Resource location is not allowlisted: " + location);
        }

        String normalizedLocation = Objects.requireNonNull(location);
        Resource resource = resourcePatternResolver.getResource(normalizedLocation);
        if (!resource.exists() || !resource.isReadable()) {
            return ToolResult.error(null, "Resource is not readable: " + normalizedLocation);
        }

        try {
            return ToolResult.success(null, BuiltInResourceSupport.payload(
                    "resource",
                    normalizedLocation,
                    resource,
                    BuiltInResourceSupport.readBytes(resource)));
        } catch (IOException e) {
            return ToolResult.error(null, "Failed to read resource: " + normalizedLocation);
        }
    }

    private String extractLocation(Object input) {
        if (!(input instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("resource_reader requires a non-blank 'location' field.");
        }
        Object rawLocation = map.get("location");
        if (!(rawLocation instanceof String location) || location.isBlank()) {
            throw new IllegalArgumentException("resource_reader requires a non-blank 'location' field.");
        }
        return accessPolicy.normalizeResourceLocation(location);
    }

    private static ObjectNode inputSchema() {
        ObjectNode root = JSON.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ObjectNode location = properties.putObject("location");
        location.put("type", "string");
        location.put("description", "Allowlisted resource location starting with classpath: or file:.");
        root.putArray("required").add("location");
        root.put("additionalProperties", false);
        return root;
    }
}