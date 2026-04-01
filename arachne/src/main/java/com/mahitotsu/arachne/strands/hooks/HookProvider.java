package com.mahitotsu.arachne.strands.hooks;

/**
 * Contributes hook callbacks to a runtime-local {@link HookRegistry}.
 */
public interface HookProvider {

    void registerHooks(HookRegistrar registrar);
}