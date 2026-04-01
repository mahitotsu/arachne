package com.mahitotsu.arachne.strands.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session-scoped key-value state exposed by an {@link Agent}.
 */
public final class AgentState {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public AgentState() {
    }

    public AgentState(Map<String, Object> initialValues) {
        replaceWith(initialValues);
    }

    public Map<String, Object> get() {
        return copyMap(values);
    }

    public Object get(String key) {
        return copyValue(values.get(key));
    }

    public void put(String key, Object value) {
        values.put(key, copyValue(value));
    }

    public void putAll(Map<String, Object> additionalValues) {
        if (additionalValues == null) {
            return;
        }
        additionalValues.forEach(this::put);
    }

    public Object remove(String key) {
        Object removed = values.remove(key);
        return copyValue(removed);
    }

    public void clear() {
        values.clear();
    }

    public void replaceWith(Map<String, Object> newValues) {
        values.clear();
        if (newValues != null) {
            values.putAll(copyMap(newValues));
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