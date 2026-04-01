package com.mahitotsu.arachne.strands.spring;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.NonNull;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mahitotsu.arachne.strands.prompt.PromptTemplate;
import com.mahitotsu.arachne.strands.prompt.PromptVariables;

/**
 * Spring-managed helper for rendering an already-materialized structured-output object to text.
 *
 * <p>The renderer resolves an explicit Spring {@code Resource} location, reads the template as UTF-8,
 * and applies the same named-placeholder syntax used by {@link PromptTemplate}. The typed model is
 * converted to top-level template variables through the configured {@link ObjectMapper}. Scalar fields
 * are rendered as plain text; nested objects and arrays are rendered as compact JSON strings.
 */
public class ArachneTemplateRenderer {

    private final ResourcePatternResolver resourcePatternResolver;
    private final ObjectMapper objectMapper;

    public ArachneTemplateRenderer(ResourcePatternResolver resourcePatternResolver, ObjectMapper objectMapper) {
        this.resourcePatternResolver = Objects.requireNonNull(resourcePatternResolver,
                "resourcePatternResolver must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Renders the supplied typed model with the template at the given Spring resource location.
     *
     * <p>The location must be explicit, for example {@code classpath:/templates/trip-plan.txt}.
     * The model is expected to serialize to a JSON object; top-level properties become available as
     * template variables.
     */
    public String render(@NonNull String templateLocation, @NonNull Object model) {
        String location = Objects.requireNonNull(templateLocation, "templateLocation must not be null");
        Object typedModel = Objects.requireNonNull(model, "model must not be null");

        String templateText = readTemplate(location);
        PromptTemplate template = PromptTemplate.of(templateText);
        PromptVariables variables = PromptVariables.from(extractVariables(typedModel, location));
        try {
            return template.render(variables);
        } catch (IllegalArgumentException e) {
            throw new ArachneTemplateRenderException(
                    "Template rendering failed for " + location + ": " + e.getMessage(),
                    e);
        }
    }

    private String readTemplate(@NonNull String templateLocation) {
        Resource resource = resourcePatternResolver.getResource(templateLocation);
        if (!resource.exists()) {
            throw new ArachneTemplateNotFoundException(templateLocation);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, Objects.requireNonNull(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ArachneTemplateRenderException("Failed to read template resource: " + templateLocation, e);
        }
    }

    private Map<String, String> extractVariables(@NonNull Object model, @NonNull String templateLocation) {
        JsonNode root = objectMapper.valueToTree(model);
        if (root == null || !root.isObject()) {
            throw new ArachneTemplateRenderException(
                    "Template rendering requires a structured object model for " + templateLocation);
        }

        Map<String, String> variables = new LinkedHashMap<>();
        root.properties().forEach(entry -> {
            String variableName = Objects.requireNonNull(entry.getKey());
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                return;
            }
            variables.put(variableName, stringifyValue(value, templateLocation, variableName));
        });
        return variables;
    }

    private String stringifyValue(@NonNull JsonNode value, @NonNull String templateLocation, @NonNull String variableName) {
        if (value.isValueNode()) {
            return value.asText();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ArachneTemplateRenderException(
                    "Failed to serialize template variable '" + variableName + "' for " + templateLocation,
                    e);
        }
    }
}