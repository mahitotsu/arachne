package io.arachne.strands.tool.annotation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import io.arachne.strands.tool.Tool;

/**
 * Tool metadata discovered from annotation scanning.
 */
public final class DiscoveredTool {

    private final Tool tool;
    private final Set<String> qualifiers;

    public DiscoveredTool(Tool tool, Set<String> qualifiers) {
        this.tool = Objects.requireNonNull(tool, "tool must not be null");
        this.qualifiers = normalizeQualifiers(qualifiers);
    }

    public Tool tool() {
        return tool;
    }

    public Set<String> qualifiers() {
        return qualifiers;
    }

    public boolean matchesAny(Set<String> selectedQualifiers) {
        if (selectedQualifiers.isEmpty()) {
            return true;
        }
        return qualifiers.stream().anyMatch(selectedQualifiers::contains);
    }

    private static Set<String> normalizeQualifiers(Set<String> qualifiers) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String qualifier : qualifiers == null ? Set.<String>of() : qualifiers) {
            if (qualifier != null && !qualifier.isBlank()) {
                normalized.add(qualifier);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }
}