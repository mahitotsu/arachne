package com.mahitotsu.arachne.strands.prompt;

import java.util.HashMap;
import java.util.Map;

/**
 * An immutable map of named variables used when rendering a {@link PromptTemplate}.
 *
 * <p>Use the static factory methods to construct instances:
 * <pre>{@code
 * PromptVariables vars = PromptVariables.of("name", "Alice", "role", "assistant");
 * }</pre>
 */
public final class PromptVariables {

    private final Map<String, String> variables;

    private PromptVariables(Map<String, String> variables) {
        this.variables = Map.copyOf(variables);
    }

    /**
     * Creates an empty variable map.
     */
    public static PromptVariables empty() {
        return new PromptVariables(Map.of());
    }

    /**
     * Creates a variable map from a flat key-value sequence.
     *
     * <p>The arguments must form an even-length sequence of alternating keys and values.
     * Keys and values must not be {@code null}.
     *
     * @throws IllegalArgumentException if the argument count is odd, or if any key or value is null
     */
    public static PromptVariables of(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "PromptVariables.of requires an even number of arguments (key, value, ...)");
        }
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String key = keyValuePairs[i];
            String value = keyValuePairs[i + 1];
            if (key == null) {
                throw new IllegalArgumentException("Variable key at index " + i + " must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("Variable value for key \"" + key + "\" must not be null");
            }
            map.put(key, value);
        }
        return new PromptVariables(map);
    }

    /**
     * Creates a variable map from an existing {@link Map}.
     *
     * @throws IllegalArgumentException if any key or value is null
     */
    public static PromptVariables from(Map<String, String> map) {
        map.forEach((k, v) -> {
            if (k == null) {
                throw new IllegalArgumentException("Variable key must not be null");
            }
            if (v == null) {
                throw new IllegalArgumentException("Variable value for key \"" + k + "\" must not be null");
            }
        });
        return new PromptVariables(map);
    }

    /**
     * Returns the value for the given variable name, or {@code null} if absent.
     */
    String get(String name) {
        return variables.get(name);
    }

    /**
     * Returns {@code true} if the given variable name is present.
     */
    boolean contains(String name) {
        return variables.containsKey(name);
    }
}
