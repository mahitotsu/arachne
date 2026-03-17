package io.arachne.strands.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-schema description of a tool exposed to the model.
 */
public record ToolSpec(
        String name,
        String description,
        JsonNode inputSchema
) {}
