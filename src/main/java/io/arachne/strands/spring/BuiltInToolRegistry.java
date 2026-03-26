package io.arachne.strands.spring;

import java.util.List;

/**
 * Dedicated registry wrapper for framework-provided built-in tools.
 */
public record BuiltInToolRegistry(List<BuiltInToolDefinition> definitions) {

    public BuiltInToolRegistry {
        definitions = definitions == null ? List.of() : List.copyOf(definitions);
    }

    public static BuiltInToolRegistry empty() {
        return new BuiltInToolRegistry(List.of());
    }
}