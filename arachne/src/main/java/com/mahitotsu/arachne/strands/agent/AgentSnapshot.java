package com.mahitotsu.arachne.strands.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned in-memory snapshot of an {@link Agent} runtime state.
 */
public record AgentSnapshot(
        String scope,
        String schemaVersion,
        String createdAt,
        Map<String, Object> data,
        Map<String, Object> appData
) {

    public static final String SCOPE_AGENT = "agent";
    public static final String SCHEMA_VERSION_1_0 = "1.0";

    public static final String FIELD_MESSAGES = "messages";
    public static final String FIELD_STATE = "state";
    public static final String FIELD_CONVERSATION_MANAGER_STATE = "conversation_manager_state";
    public static final String FIELD_INTERRUPT_STATE = "interrupt_state";

    public AgentSnapshot(Map<String, Object> data, Map<String, Object> appData) {
        this(SCOPE_AGENT, SCHEMA_VERSION_1_0, Instant.now().toString(), data, appData);
    }

    public AgentSnapshot {
        scope = scope == null || scope.isBlank() ? SCOPE_AGENT : scope;
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? SCHEMA_VERSION_1_0 : schemaVersion;
        createdAt = createdAt == null || createdAt.isBlank() ? Instant.now().toString() : createdAt;
        data = Collections.unmodifiableMap(copyMap(Objects.requireNonNull(data, "data must not be null")));
        appData = Collections.unmodifiableMap(copyMap(appData == null ? Map.of() : appData));
    }

    public void validate() {
        if (!SCHEMA_VERSION_1_0.equals(schemaVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported snapshot schema version: '" + schemaVersion + "'. Current version: " + SCHEMA_VERSION_1_0);
        }
        if (!SCOPE_AGENT.equals(scope)) {
            throw new IllegalArgumentException("Invalid snapshot scope: '" + scope + "'. Valid scopes: [agent]");
        }
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                copied.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return copied;
        }
        if (value instanceof List<?> listValue) {
            List<Object> copied = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                copied.add(copyValue(item));
            }
            return copied;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return (Map<String, Object>) copyValue(source);
    }
}