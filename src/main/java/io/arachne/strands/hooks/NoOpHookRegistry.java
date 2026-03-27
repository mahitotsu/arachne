package io.arachne.strands.hooks;

/**
 * No-op hook registry used by default when no hooks are registered.
 *
 * <p>All methods inherit the empty default implementations from {@link HookRegistry},
 * while dispatching registries can be provided explicitly when hook handling is needed.
 */
public final class NoOpHookRegistry implements HookRegistry {
    // inherits all no-op defaults from HookRegistry
}
