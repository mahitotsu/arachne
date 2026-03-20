package io.arachne.strands.spring;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.agent.AgentState;
import io.arachne.strands.agent.DefaultAgent;
import io.arachne.strands.agent.conversation.ConversationManager;
import io.arachne.strands.agent.conversation.SlidingWindowConversationManager;
import io.arachne.strands.eventloop.EventLoop;
import io.arachne.strands.hooks.DispatchingHookRegistry;
import io.arachne.strands.hooks.HookProvider;
import io.arachne.strands.hooks.HookRegistry;
import io.arachne.strands.hooks.NoOpHookRegistry;
import io.arachne.strands.hooks.Plugin;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.bedrock.BedrockModel;
import io.arachne.strands.model.retry.ModelRetryStrategy;
import io.arachne.strands.model.retry.RetryingModel;
import io.arachne.strands.session.SessionManager;
import io.arachne.strands.tool.BeanValidationSupport;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolExecutionMode;
import io.arachne.strands.tool.ToolExecutor;
import io.arachne.strands.tool.annotation.DiscoveredTool;
import jakarta.validation.Validator;

/**
 * Factory for creating {@link Agent} instances.
 * Injected automatically by {@link ArachneAutoConfiguration}.
 *
 * <p>Usage:
 * <pre>{@code
 * @Autowired AgentFactory agentFactory;
 *
 * Agent agent = agentFactory.builder()
 *     .tools(myTool1, myTool2)
 *     .systemPrompt("You are a helpful assistant.")
 *     .build();
 *
 * AgentResult result = agent.run("Summarise this document.");
 * }</pre>
 */
public class AgentFactory {

    private final ArachneProperties properties;
    private final Model defaultModel;
    private final List<DiscoveredTool> discoveredTools;
    private final List<HookProvider> discoveredHooks;
    private final Validator validator;
    private final SessionManager defaultSessionManager;
    private final ModelRetryStrategy defaultRetryStrategy;
    private final ObjectMapper objectMapper;
    private final Executor defaultToolExecutionExecutor;

    public AgentFactory(ArachneProperties properties) {
        this(properties, null, List.of(), List.of(), BeanValidationSupport.defaultValidator(), null, null, new ObjectMapper(), null);
    }

    public AgentFactory(ArachneProperties properties, Model defaultModel) {
        this(properties, defaultModel, List.of(), List.of(), BeanValidationSupport.defaultValidator(), null, null, new ObjectMapper(), null);
    }

    public AgentFactory(ArachneProperties properties, Model defaultModel, List<DiscoveredTool> discoveredTools) {
        this(properties, defaultModel, discoveredTools, List.of(), BeanValidationSupport.defaultValidator(), null, null, new ObjectMapper(), null);
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            List<DiscoveredTool> discoveredTools,
            List<HookProvider> discoveredHooks) {
        this(properties, defaultModel, discoveredTools, discoveredHooks, BeanValidationSupport.defaultValidator(), null, null, new ObjectMapper(), null);
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            List<DiscoveredTool> discoveredTools,
            Validator validator) {
        this(properties, defaultModel, discoveredTools, List.of(), validator, null, null, new ObjectMapper(), null);
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            List<DiscoveredTool> discoveredTools,
            Validator validator,
            SessionManager defaultSessionManager) {
        this(properties, defaultModel, discoveredTools, List.of(), validator, defaultSessionManager, null, new ObjectMapper(), null);
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            List<DiscoveredTool> discoveredTools,
            Validator validator,
            SessionManager defaultSessionManager,
            ModelRetryStrategy defaultRetryStrategy) {
        this(properties, defaultModel, discoveredTools, List.of(), validator, defaultSessionManager, defaultRetryStrategy, new ObjectMapper(), null);
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            List<DiscoveredTool> discoveredTools,
            List<HookProvider> discoveredHooks,
            Validator validator,
            SessionManager defaultSessionManager,
            ModelRetryStrategy defaultRetryStrategy,
            ObjectMapper objectMapper,
            Executor defaultToolExecutionExecutor) {
        this.properties = properties;
        this.defaultModel = defaultModel;
        this.discoveredTools = List.copyOf(discoveredTools);
        this.discoveredHooks = List.copyOf(discoveredHooks);
        this.validator = validator;
        this.defaultSessionManager = defaultSessionManager;
        this.defaultRetryStrategy = defaultRetryStrategy;
        this.objectMapper = objectMapper;
        this.defaultToolExecutionExecutor = defaultToolExecutionExecutor;
    }

    public Builder builder() {
        return new Builder(resolveBuilderDefaults(null), discoveredTools, discoveredHooks, validator, defaultSessionManager, objectMapper, defaultToolExecutionExecutor);
    }

    public Builder builder(String name) {
        return new Builder(resolveBuilderDefaults(name), discoveredTools, discoveredHooks, validator, defaultSessionManager, objectMapper, defaultToolExecutionExecutor);
    }

    static Model createDefaultModel(ArachneProperties properties) {
        return createDefaultModel(properties.getModel());
    }

    static Model createDefaultModel(ArachneProperties.ModelProperties modelProperties) {
        String provider = modelProperties.getProvider();
        if (!"bedrock".equalsIgnoreCase(provider)) {
            throw new UnsupportedModelProviderException(provider);
        }

        String modelId = modelProperties.getId();
        String region = modelProperties.getRegion();
        if (modelId != null && !modelId.isBlank()) {
            return new BedrockModel(modelId, region);
        }
        if (region != null && !region.isBlank()) {
            return new BedrockModel(BedrockModel.DEFAULT_MODEL_ID, region);
        }
        return new BedrockModel();
    }

    private BuilderDefaults resolveBuilderDefaults(String name) {
        if (name == null || name.isBlank()) {
            return new BuilderDefaults(
                    copyModelProperties(properties.getModel()),
                    defaultModel,
                    properties.getAgent().getSystemPrompt(),
                    ToolExecutionMode.PARALLEL,
                    true,
                    Set.of(),
                    properties.getAgent().getConversation().getWindowSize(),
                    properties.getAgent().getSession().getId(),
                    defaultRetryStrategy);
        }

        ArachneProperties.NamedAgentProperties namedProperties = properties.getAgents().get(name);
        if (namedProperties == null) {
            throw new NamedAgentNotFoundException(name);
        }

        ArachneProperties.ModelProperties mergedModel = mergeModelProperties(properties.getModel(), namedProperties.getModel());
        Model namedDefaultModel = hasModelOverride(namedProperties.getModel())
                ? createDefaultModel(mergedModel)
                : defaultModel;
        Integer namedWindowSize = namedProperties.getConversation().getWindowSize();
        Boolean useDiscoveredTools = namedProperties.getUseDiscoveredTools();
        return new BuilderDefaults(
                mergedModel,
                namedDefaultModel,
                firstNonBlank(namedProperties.getSystemPrompt(), properties.getAgent().getSystemPrompt()),
                namedProperties.getToolExecutionMode() != null ? namedProperties.getToolExecutionMode() : ToolExecutionMode.PARALLEL,
                useDiscoveredTools != null ? useDiscoveredTools : true,
                normalizeQualifiers(namedProperties.getToolQualifiers()),
                namedWindowSize != null
                        ? namedWindowSize
                        : properties.getAgent().getConversation().getWindowSize(),
                firstNonBlank(namedProperties.getSession().getId(), properties.getAgent().getSession().getId()),
                resolveNamedRetryStrategy(namedProperties.getRetry()));
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
        return new io.arachne.strands.model.retry.ExponentialBackoffRetryStrategy(maxAttempts, initialDelay, maxDelay);
    }

    private static boolean hasRetryOverride(ArachneProperties.RetryOverrideProperties retryProperties) {
        return retryProperties != null
                && (retryProperties.getEnabled() != null
                || retryProperties.getMaxAttempts() != null
                || retryProperties.getInitialDelay() != null
                || retryProperties.getMaxDelay() != null);
    }

    private static boolean hasModelOverride(ArachneProperties.ModelOverrideProperties modelProperties) {
        return modelProperties != null
                && (hasText(modelProperties.getProvider())
                || hasText(modelProperties.getId())
                || hasText(modelProperties.getRegion()));
    }

    private static ArachneProperties.ModelProperties mergeModelProperties(
            ArachneProperties.ModelProperties defaults,
            ArachneProperties.ModelOverrideProperties overrides) {
        ArachneProperties.ModelProperties merged = copyModelProperties(defaults);
        if (overrides == null) {
            return merged;
        }
        if (hasText(overrides.getProvider())) {
            merged.setProvider(overrides.getProvider());
        }
        if (hasText(overrides.getId())) {
            merged.setId(overrides.getId());
        }
        if (hasText(overrides.getRegion())) {
            merged.setRegion(overrides.getRegion());
        }
        return merged;
    }

    private static ArachneProperties.ModelProperties copyModelProperties(ArachneProperties.ModelProperties source) {
        ArachneProperties.ModelProperties copy = new ArachneProperties.ModelProperties();
        copy.setProvider(source.getProvider());
        copy.setId(source.getId());
        copy.setRegion(source.getRegion());
        return copy;
    }

    private static Set<String> normalizeQualifiers(List<String> toolQualifiers) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String toolQualifier : toolQualifiers == null ? List.<String>of() : toolQualifiers) {
            if (hasText(toolQualifier)) {
                normalized.add(toolQualifier);
            }
        }
        return Set.copyOf(normalized);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return hasText(preferred) ? preferred : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record BuilderDefaults(
            ArachneProperties.ModelProperties modelProperties,
            Model defaultModel,
            String systemPrompt,
            ToolExecutionMode toolExecutionMode,
            boolean useDiscoveredTools,
            Set<String> toolQualifiers,
            int conversationWindowSize,
            String sessionId,
            ModelRetryStrategy retryStrategy) {
    }

    public static class Builder {

        private final BuilderDefaults defaults;
        private final Model defaultModel;
        private final List<DiscoveredTool> discoveredTools;
        private final List<HookProvider> discoveredHooks;
        private final Validator validator;
        private final SessionManager defaultSessionManager;
        private final ObjectMapper objectMapper;
        private final Executor defaultToolExecutionExecutor;
        private Model model;
        private List<Tool> tools = List.of();
        private String systemPrompt;
        private ToolExecutionMode toolExecutionMode;
        private boolean useDiscoveredTools;
        private Set<String> toolQualifiers;
        private ConversationManager conversationManager;
        private SessionManager sessionManager;
        private ModelRetryStrategy retryStrategy;
        private String sessionId;
        private Executor toolExecutionExecutor;
        private AgentState state = new AgentState();
        private List<HookProvider> hookProviders = List.of();
        private List<Plugin> plugins = List.of();

        private Builder(
                BuilderDefaults defaults,
                List<DiscoveredTool> discoveredTools,
            List<HookProvider> discoveredHooks,
                Validator validator,
                SessionManager defaultSessionManager,
                ObjectMapper objectMapper,
                Executor defaultToolExecutionExecutor) {
            this.defaults = Objects.requireNonNull(defaults, "defaults must not be null");
            this.defaultModel = defaults.defaultModel();
            this.discoveredTools = List.copyOf(discoveredTools);
            this.discoveredHooks = List.copyOf(discoveredHooks);
            this.validator = validator;
            this.defaultSessionManager = defaultSessionManager;
            this.objectMapper = objectMapper;
            this.defaultToolExecutionExecutor = defaultToolExecutionExecutor;
            this.systemPrompt = defaults.systemPrompt();
            this.toolExecutionMode = defaults.toolExecutionMode();
            this.useDiscoveredTools = defaults.useDiscoveredTools();
            this.toolQualifiers = defaults.toolQualifiers();
            this.retryStrategy = defaults.retryStrategy();
            this.sessionId = defaults.sessionId();
        }

        public Builder model(Model model) {
            this.model = model;
            return this;
        }

        public Builder tools(Tool... tools) {
            this.tools = Stream.concat(this.tools.stream(), List.of(tools).stream()).toList();
            return this;
        }

        public Builder tools(List<Tool> tools) {
            this.tools = Stream.concat(this.tools.stream(), tools.stream()).toList();
            return this;
        }

        public Builder hooks(HookProvider... hookProviders) {
            this.hookProviders = Stream.concat(this.hookProviders.stream(), List.of(hookProviders).stream()).toList();
            return this;
        }

        public Builder hooks(List<? extends HookProvider> hookProviders) {
            this.hookProviders = Stream.concat(this.hookProviders.stream(), hookProviders.stream()).toList();
            return this;
        }

        public Builder plugins(Plugin... plugins) {
            this.plugins = Stream.concat(this.plugins.stream(), List.of(plugins).stream()).toList();
            return this;
        }

        public Builder plugins(List<? extends Plugin> plugins) {
            this.plugins = Stream.concat(this.plugins.stream(), plugins.stream()).toList();
            return this;
        }

        public Builder toolExecutionMode(ToolExecutionMode toolExecutionMode) {
            this.toolExecutionMode = toolExecutionMode;
            return this;
        }

        public Builder toolExecutionExecutor(Executor toolExecutionExecutor) {
            this.toolExecutionExecutor = toolExecutionExecutor;
            return this;
        }

        public Builder useDiscoveredTools(boolean useDiscoveredTools) {
            this.useDiscoveredTools = useDiscoveredTools;
            return this;
        }

        public Builder toolQualifiers(String... toolQualifiers) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String toolQualifier : toolQualifiers) {
                if (toolQualifier != null && !toolQualifier.isBlank()) {
                    normalized.add(toolQualifier);
                }
            }
            this.toolQualifiers = Set.copyOf(normalized);
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder conversationManager(ConversationManager conversationManager) {
            this.conversationManager = conversationManager;
            return this;
        }

        public Builder sessionManager(SessionManager sessionManager) {
            this.sessionManager = sessionManager;
            return this;
        }

        public Builder retryStrategy(ModelRetryStrategy retryStrategy) {
            this.retryStrategy = retryStrategy;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder state(AgentState state) {
            this.state = state == null ? new AgentState() : state;
            return this;
        }

        public Builder state(java.util.Map<String, Object> state) {
            this.state = new AgentState(state);
            return this;
        }

        public Agent build() {
            Model resolvedModel = resolveModel();
            HookRegistry hooks = resolveHooks();
            EventLoop eventLoop = new EventLoop(hooks, new ToolExecutor(toolExecutionMode, resolveToolExecutionExecutor()));
            List<Tool> resolvedTools = Stream.concat(
                    Stream.concat(resolveDiscoveredTools().stream(), resolvePluginTools().stream()),
                    tools.stream()).toList();
            return new DefaultAgent(
                wrapWithRetryIfNeeded(resolvedModel),
                    resolvedTools,
                    eventLoop,
                    hooks,
                    systemPrompt,
                    validator,
                    objectMapper,
                    resolveConversationManager(),
                    resolveSessionManager(),
                    sessionId,
                    state);
        }

        private List<Tool> resolveDiscoveredTools() {
            if (!useDiscoveredTools) {
                return List.of();
            }
            return discoveredTools.stream()
                    .filter(discoveredTool -> discoveredTool.matchesAny(toolQualifiers))
                    .map(DiscoveredTool::tool)
                    .toList();
        }

        private List<Tool> resolvePluginTools() {
            return plugins.stream()
                    .flatMap(plugin -> plugin.tools().stream())
                    .toList();
        }

        private HookRegistry resolveHooks() {
            List<HookProvider> resolvedHookProviders = Stream.concat(
                    Stream.concat(discoveredHooks.stream(), hookProviders.stream()),
                    plugins.stream())
                    .toList();
            if (resolvedHookProviders.isEmpty()) {
                return new NoOpHookRegistry();
            }
            return DispatchingHookRegistry.fromProviders(resolvedHookProviders);
        }

        private Model resolveModel() {
            if (model != null) {
                return model;
            }
            if (defaultModel != null) {
                return defaultModel;
            }
            return createDefaultModel(defaults.modelProperties());
        }

        private Model wrapWithRetryIfNeeded(Model resolvedModel) {
            ModelRetryStrategy resolvedRetryStrategy = retryStrategy;
            if (resolvedRetryStrategy == null) {
                return resolvedModel;
            }
            return new RetryingModel(resolvedModel, resolvedRetryStrategy);
        }

        private ConversationManager resolveConversationManager() {
            if (conversationManager != null) {
                return conversationManager;
            }
            return new SlidingWindowConversationManager(defaults.conversationWindowSize());
        }

        private SessionManager resolveSessionManager() {
            if (sessionManager != null) {
                return sessionManager;
            }
            return defaultSessionManager;
        }

        private Executor resolveToolExecutionExecutor() {
            if (toolExecutionExecutor != null) {
                return toolExecutionExecutor;
            }
            return defaultToolExecutionExecutor;
        }
    }
}
