package com.mahitotsu.arachne.strands.hooks;

import java.util.List;

import com.mahitotsu.arachne.strands.tool.Tool;

/**
 * Bundles tools and hooks into a single reusable extension unit.
 */
public interface Plugin extends HookProvider {

    default List<Tool> tools() {
        return List.of();
    }

    @Override
    default void registerHooks(HookRegistrar registrar) {
    }
}