package com.mahitotsu.arachne.strands.spring;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.mahitotsu.arachne.strands.tool.Tool;

/**
 * Metadata for Arachne-managed built-in tools.
 */
public record BuiltInToolDefinition(
        Tool tool,
        boolean includedByDefault,
        Set<String> groups) {

    public BuiltInToolDefinition {
        tool = Objects.requireNonNull(tool, "tool must not be null");
        groups = groups == null ? Set.of() : Set.copyOf(groups);
    }

    boolean matches(Set<String> selectedNames, Set<String> selectedGroups) {
        if (selectedNames != null && selectedNames.contains(tool.spec().name())) {
            return true;
        }
        if (selectedGroups == null || selectedGroups.isEmpty()) {
            return false;
        }
        return !Collections.disjoint(groups, selectedGroups);
    }
}