package io.arachne.strands.tool.builtin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolResult;

public final class CurrentTimeTool implements Tool {

    public static final String TOOL_NAME = "current_time";
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ToolSpec spec = new ToolSpec(
            TOOL_NAME,
            "Returns the current time in UTC and in an optional requested time zone.",
            inputSchema());

    @Override
    public ToolSpec spec() {
        return spec;
    }

    @Override
    public ToolResult invoke(Object input) {
        ZoneId zoneId;
        try {
            zoneId = resolveZoneId(input);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(null, e.getMessage());
        }

        Instant now = Instant.now();
        ZonedDateTime zonedDateTime = now.atZone(zoneId);
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "current_time");
        payload.put("zoneId", zoneId.getId());
        payload.put("instant", now.toString());
        payload.put("zonedDateTime", zonedDateTime.toString());
        payload.put("localDate", zonedDateTime.toLocalDate().toString());
        payload.put("localTime", zonedDateTime.toLocalTime().toString());
        return ToolResult.success(null, Map.copyOf(payload));
    }

    private ZoneId resolveZoneId(Object input) {
        if (!(input instanceof Map<?, ?> map) || map.isEmpty()) {
            return ZoneId.of("UTC");
        }
        Object rawZoneId = map.get("zoneId");
        if (rawZoneId == null) {
            return ZoneId.of("UTC");
        }
        if (!(rawZoneId instanceof String zoneIdText) || zoneIdText.isBlank()) {
            throw new IllegalArgumentException("current_time requires 'zoneId' to be a non-blank string when provided.");
        }
        try {
            return ZoneId.of(zoneIdText.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown time zone: " + zoneIdText.trim());
        }
    }

    private static ObjectNode inputSchema() {
        ObjectNode root = JSON.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ObjectNode zoneId = properties.putObject("zoneId");
        zoneId.put("type", "string");
        zoneId.put("description", "Optional IANA time-zone identifier such as Asia/Tokyo. Defaults to UTC.");
        root.put("additionalProperties", false);
        return root;
    }
}