package com.mahitotsu.arachne.strands.tool.builtin;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolResult;

public final class ResourceListTool implements Tool {

    public static final String TOOL_NAME = "resource_list";
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ResourcePatternResolver resourcePatternResolver;
    private final BuiltInResourceAccessPolicy accessPolicy;
    private final ToolSpec spec = new ToolSpec(
            TOOL_NAME,
            "Lists allowlisted classpath or file resources under a directory root.",
            inputSchema());

    public ResourceListTool(ResourcePatternResolver resourcePatternResolver, BuiltInResourceAccessPolicy accessPolicy) {
        this.resourcePatternResolver = Objects.requireNonNull(resourcePatternResolver, "resourcePatternResolver must not be null");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy must not be null");
    }

    @Override
    public ToolSpec spec() {
        return spec;
    }

    @Override
    public ToolResult invoke(Object input) {
        ListRequest request;
        try {
            request = extractRequest(input);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(null, e.getMessage());
        }

        if (!accessPolicy.isAllowed(request.location())) {
            return ToolResult.error(null, "Resource location is not allowlisted: " + request.location());
        }

        try {
                String location = Objects.requireNonNull(request.location());
                Resource root = resourcePatternResolver.getResource(location);
                String patternLocation = toPatternLocation(location) + request.pattern();
            Resource[] resources = resourcePatternResolver.getResources(patternLocation);
            List<String> matches = java.util.Arrays.stream(resources)
                    .filter(Resource::exists)
                    .filter(Resource::isReadable)
                    .filter(resource -> !resourceIsDirectory(resource))
                    .map(resource -> toListedLocation(location, root, resource))
                    .distinct()
                    .sorted()
                    .toList();

            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "resource_list");
                payload.put("location", location);
            payload.put("pattern", request.pattern());
            payload.put("resources", matches);
            return ToolResult.success(null, Map.copyOf(payload));
        } catch (IOException e) {
            return ToolResult.error(null, "Failed to list resources under: " + request.location());
        }
    }

    private ListRequest extractRequest(Object input) {
        if (!(input instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("resource_list requires a non-blank 'location' field.");
        }
        Object rawLocation = map.get("location");
        if (!(rawLocation instanceof String location) || location.isBlank()) {
            throw new IllegalArgumentException("resource_list requires a non-blank 'location' field.");
        }
        Object rawPattern = map.get("pattern");
        String pattern = rawPattern instanceof String patternText && !patternText.isBlank()
                ? patternText.trim()
                : "**/*";
        return new ListRequest(accessPolicy.normalizeDirectoryLocation(location), pattern);
    }

    private static String toPatternLocation(String location) {
        if (location.startsWith("classpath:/")) {
            return "classpath*:" + location.substring("classpath:".length());
        }
        return location;
    }

    private static boolean resourceIsDirectory(Resource resource) {
        try {
            return resource.getFile().isDirectory();
        } catch (IOException e) {
            return false;
        }
    }

    private static String toListedLocation(String rootLocation, Resource rootResource, Resource resource) {
        try {
            String relative = rootResource.getURI().relativize(resource.getURI()).getPath();
            if (relative == null || relative.isBlank()) {
                return rootLocation;
            }
            return rootLocation + relative.replace('\\', '/');
        } catch (IOException e) {
            return rootLocation + Objects.requireNonNullElse(resource.getFilename(), "unknown");
        }
    }

    private static ObjectNode inputSchema() {
        ObjectNode root = JSON.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ObjectNode location = properties.putObject("location");
        location.put("type", "string");
        location.put("description", "Allowlisted directory root starting with classpath: or file:.");
        ObjectNode pattern = properties.putObject("pattern");
        pattern.put("type", "string");
        pattern.put("description", "Optional Ant-style pattern under the root. Defaults to **/*.");
        root.putArray("required").add("location");
        root.put("additionalProperties", false);
        return root;
    }

    private record ListRequest(String location, String pattern) {
    }
}