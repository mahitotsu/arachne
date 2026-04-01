package com.mahitotsu.arachne.strands.spring;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.retry.ExponentialBackoffRetryStrategy;
import com.mahitotsu.arachne.strands.model.retry.ModelRetryStrategy;
import com.mahitotsu.arachne.strands.tool.ToolExecutionMode;

final class AgentFactoryDefaultsResolver {

    private final ArachneProperties properties;
    private final Model defaultModel;
    private final ModelRetryStrategy defaultRetryStrategy;

    AgentFactoryDefaultsResolver(
            ArachneProperties properties,
            Model defaultModel,
            ModelRetryStrategy defaultRetryStrategy) {
        this.properties = properties;
        this.defaultModel = defaultModel;
        this.defaultRetryStrategy = defaultRetryStrategy;
    }

    BuilderDefaults resolve(String name) {
        if (name == null || name.isBlank()) {
            return resolveDefaultBuilderDefaults();
        }

        return resolveNamedBuilderDefaults(requireNamedAgentProperties(name));
    }

    static Set<String> normalizeNames(List<String> values) {
        return normalizeStrings(values);
    }

    static Set<String> normalizeQualifiers(List<String> values) {
        return normalizeStrings(values);
    }

    private BuilderDefaults resolveDefaultBuilderDefaults() {
        ArachneProperties.BuiltInToolProperties builtIns = properties.getAgent().getBuiltIns();
        return new BuilderDefaults(
                AgentFactoryModelResolver.copyModelProperties(properties.getModel()),
                defaultModel,
                properties.getAgent().getSystemPrompt(),
                ToolExecutionMode.PARALLEL,
                true,
                Set.of(),
                builtIns.isInheritDefaults(),
                normalizeNames(builtIns.getToolNames()),
                normalizeNames(builtIns.getToolGroups()),
                properties.getAgent().getConversation().getWindowSize(),
                properties.getAgent().getSession().getId(),
                defaultRetryStrategy);
    }

    private ArachneProperties.NamedAgentProperties requireNamedAgentProperties(String name) {
        ArachneProperties.NamedAgentProperties namedProperties = properties.getAgents().get(name);
        if (namedProperties == null) {
            throw new NamedAgentNotFoundException(name);
        }
        return namedProperties;
    }

    private BuilderDefaults resolveNamedBuilderDefaults(ArachneProperties.NamedAgentProperties namedProperties) {
        AgentFactoryModelResolver.ResolvedModelDefaults resolvedModelDefaults = AgentFactoryModelResolver.resolveNamedModelDefaults(
                properties.getModel(),
                namedProperties.getModel(),
                defaultModel);
        ArachneProperties.BuiltInToolProperties defaultBuiltIns = properties.getAgent().getBuiltIns();
        ArachneProperties.BuiltInToolOverrideProperties namedBuiltIns = namedProperties.getBuiltIns();
        boolean inheritBuiltInTools = defaultBuiltIns.isInheritDefaults();
        if (namedBuiltIns.getInheritDefaults() != null) {
            inheritBuiltInTools = namedBuiltIns.getInheritDefaults();
        }
        return new BuilderDefaults(
                resolvedModelDefaults.modelProperties(),
                resolvedModelDefaults.defaultModel(),
                firstNonBlank(namedProperties.getSystemPrompt(), properties.getAgent().getSystemPrompt()),
                resolveNamedToolExecutionMode(namedProperties),
                resolveNamedUseDiscoveredTools(namedProperties),
                normalizeQualifiers(namedProperties.getToolQualifiers()),
                inheritBuiltInTools,
                union(normalizeNames(defaultBuiltIns.getToolNames()), normalizeNames(namedBuiltIns.getToolNames())),
                union(normalizeNames(defaultBuiltIns.getToolGroups()), normalizeNames(namedBuiltIns.getToolGroups())),
                resolveNamedConversationWindowSize(namedProperties),
                resolveNamedSessionId(namedProperties),
                resolveNamedRetryStrategy(namedProperties.getRetry()));
    }

    private ToolExecutionMode resolveNamedToolExecutionMode(ArachneProperties.NamedAgentProperties namedProperties) {
        return namedProperties.getToolExecutionMode() != null
                ? namedProperties.getToolExecutionMode()
                : ToolExecutionMode.PARALLEL;
    }

    private boolean resolveNamedUseDiscoveredTools(ArachneProperties.NamedAgentProperties namedProperties) {
        Boolean useDiscoveredTools = namedProperties.getUseDiscoveredTools();
        if (useDiscoveredTools == null) {
            return true;
        }
        return useDiscoveredTools;
    }

    private int resolveNamedConversationWindowSize(ArachneProperties.NamedAgentProperties namedProperties) {
        Integer namedWindowSize = namedProperties.getConversation().getWindowSize();
        return namedWindowSize != null
                ? namedWindowSize
                : properties.getAgent().getConversation().getWindowSize();
    }

    private String resolveNamedSessionId(ArachneProperties.NamedAgentProperties namedProperties) {
        return firstNonBlank(namedProperties.getSession().getId(), properties.getAgent().getSession().getId());
    }

    private ModelRetryStrategy resolveNamedRetryStrategy(ArachneProperties.RetryOverrideProperties retryProperties) {
        if (!hasRetryOverride(retryProperties)) {
            return defaultRetryStrategy;
        }

        Boolean enabledOverride = retryProperties.getEnabled();
        boolean enabled;
        if (enabledOverride != null) {
            enabled = enabledOverride;
        } else {
            enabled = properties.getAgent().getRetry().isEnabled();
        }
        if (!enabled) {
            return null;
        }

        ArachneProperties.RetryProperties defaults = properties.getAgent().getRetry();
        Integer maxAttemptsOverride = retryProperties.getMaxAttempts();
        int maxAttempts = maxAttemptsOverride != null
                ? maxAttemptsOverride
                : defaults.getMaxAttempts();
        Duration initialDelay = retryProperties.getInitialDelay() != null
                ? retryProperties.getInitialDelay()
                : defaults.getInitialDelay();
        Duration maxDelay = retryProperties.getMaxDelay() != null
                ? retryProperties.getMaxDelay()
                : defaults.getMaxDelay();
        return new ExponentialBackoffRetryStrategy(maxAttempts, initialDelay, maxDelay);
    }

    private static boolean hasRetryOverride(ArachneProperties.RetryOverrideProperties retryProperties) {
        return retryProperties != null
                && (retryProperties.getEnabled() != null
                || retryProperties.getMaxAttempts() != null
                || retryProperties.getInitialDelay() != null
                || retryProperties.getMaxDelay() != null);
    }

    private static Set<String> normalizeStrings(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (hasText(value)) {
                normalized.add(value);
            }
        }
        return Set.copyOf(normalized);
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(left);
        merged.addAll(right);
        return Set.copyOf(merged);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return hasText(preferred) ? preferred : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record BuilderDefaults(
            ArachneProperties.ModelProperties modelProperties,
            Model defaultModel,
            String systemPrompt,
            ToolExecutionMode toolExecutionMode,
            boolean useDiscoveredTools,
            Set<String> toolQualifiers,
            boolean inheritBuiltInTools,
            Set<String> builtInToolNames,
            Set<String> builtInToolGroups,
            int conversationWindowSize,
            String sessionId,
            ModelRetryStrategy retryStrategy) {
    }
}