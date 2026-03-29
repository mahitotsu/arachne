package io.arachne.strands.spring;

import java.time.Duration;
import java.util.ArrayList;
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
import io.arachne.strands.skills.AgentSkillsPlugin;
import io.arachne.strands.skills.Skill;
import io.arachne.strands.steering.SteeringHandler;
import io.arachne.strands.tool.BeanValidationSupport;
import io.arachne.strands.tool.ExecutionContextPropagation;
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
    private final List<BuiltInToolDefinition> builtInTools;
    private final List<DiscoveredTool> discoveredTools;
    private final List<HookProvider> discoveredHooks;
    private final List<Skill> discoveredSkills;
    private final Validator validator;
    private final SessionManager defaultSessionManager;
    private final ModelRetryStrategy defaultRetryStrategy;
    private final ObjectMapper objectMapper;
    private final Executor defaultToolExecutionExecutor;
    private final ExecutionContextPropagation defaultExecutionContextPropagation;

    public AgentFactory(ArachneProperties properties) {
        this(properties, null, BuiltInToolRegistry.empty(), List.of(), List.of(), List.of(), BeanValidationSupport.defaultValidator(), null, null, new ObjectMapper(), null, ExecutionContextPropagation.noop());
    }

    public AgentFactory(ArachneProperties properties, Model defaultModel) {
        this(properties, defaultModel, BuiltInToolRegistry.empty(), List.of(), List.of(), List.of(), BeanValidationSupport.defaultValidator(), null, null, new ObjectMapper(), null, ExecutionContextPropagation.noop());
    }

    public AgentFactory(ArachneProperties properties, Model defaultModel, List<DiscoveredTool> discoveredTools) {
        this(properties, defaultModel, BuiltInToolRegistry.empty(), discoveredTools, List.of(), List.of(), BeanValidationSupport.defaultValidator(), null, null, new ObjectMapper(), null, ExecutionContextPropagation.noop());
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            List<DiscoveredTool> discoveredTools,
            List<HookProvider> discoveredHooks) {
        this(properties, defaultModel, BuiltInToolRegistry.empty(), discoveredTools, discoveredHooks, List.of(), BeanValidationSupport.defaultValidator(), null, null, new ObjectMapper(), null, ExecutionContextPropagation.noop());
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            List<DiscoveredTool> discoveredTools,
            Validator validator) {
        this(properties, defaultModel, BuiltInToolRegistry.empty(), discoveredTools, List.of(), List.of(), validator, null, null, new ObjectMapper(), null, ExecutionContextPropagation.noop());
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            List<DiscoveredTool> discoveredTools,
            Validator validator,
            SessionManager defaultSessionManager) {
        this(properties, defaultModel, BuiltInToolRegistry.empty(), discoveredTools, List.of(), List.of(), validator, defaultSessionManager, null, new ObjectMapper(), null, ExecutionContextPropagation.noop());
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            List<DiscoveredTool> discoveredTools,
            Validator validator,
            SessionManager defaultSessionManager,
            ModelRetryStrategy defaultRetryStrategy) {
        this(properties, defaultModel, BuiltInToolRegistry.empty(), discoveredTools, List.of(), List.of(), validator, defaultSessionManager, defaultRetryStrategy, new ObjectMapper(), null, ExecutionContextPropagation.noop());
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
        this(properties, defaultModel, BuiltInToolRegistry.empty(), discoveredTools, discoveredHooks, List.of(), validator, defaultSessionManager, defaultRetryStrategy, objectMapper, defaultToolExecutionExecutor, ExecutionContextPropagation.noop());
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
            Executor defaultToolExecutionExecutor,
            ExecutionContextPropagation defaultExecutionContextPropagation) {
        this(properties, defaultModel, BuiltInToolRegistry.empty(), discoveredTools, discoveredHooks, List.of(), validator, defaultSessionManager, defaultRetryStrategy, objectMapper, defaultToolExecutionExecutor, defaultExecutionContextPropagation);
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            List<DiscoveredTool> discoveredTools,
            List<HookProvider> discoveredHooks,
            List<Skill> discoveredSkills,
            Validator validator,
            SessionManager defaultSessionManager,
            ModelRetryStrategy defaultRetryStrategy,
            ObjectMapper objectMapper,
            Executor defaultToolExecutionExecutor) {
        this(properties, defaultModel, BuiltInToolRegistry.empty(), discoveredTools, discoveredHooks, discoveredSkills, validator, defaultSessionManager, defaultRetryStrategy, objectMapper, defaultToolExecutionExecutor, ExecutionContextPropagation.noop());
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            List<DiscoveredTool> discoveredTools,
            List<HookProvider> discoveredHooks,
            List<Skill> discoveredSkills,
            Validator validator,
            SessionManager defaultSessionManager,
            ModelRetryStrategy defaultRetryStrategy,
            ObjectMapper objectMapper,
            Executor defaultToolExecutionExecutor,
            ExecutionContextPropagation defaultExecutionContextPropagation) {
        this(properties, defaultModel, BuiltInToolRegistry.empty(), discoveredTools, discoveredHooks, discoveredSkills, validator, defaultSessionManager, defaultRetryStrategy, objectMapper, defaultToolExecutionExecutor, defaultExecutionContextPropagation);
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            BuiltInToolRegistry builtInToolRegistry,
            List<DiscoveredTool> discoveredTools,
            List<HookProvider> discoveredHooks,
            Validator validator,
            SessionManager defaultSessionManager,
            ModelRetryStrategy defaultRetryStrategy,
            ObjectMapper objectMapper,
            Executor defaultToolExecutionExecutor) {
        this(properties, defaultModel, builtInToolRegistry, discoveredTools, discoveredHooks, List.of(), validator, defaultSessionManager, defaultRetryStrategy, objectMapper, defaultToolExecutionExecutor, ExecutionContextPropagation.noop());
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            BuiltInToolRegistry builtInToolRegistry,
            List<DiscoveredTool> discoveredTools,
            List<HookProvider> discoveredHooks,
            Validator validator,
            SessionManager defaultSessionManager,
            ModelRetryStrategy defaultRetryStrategy,
            ObjectMapper objectMapper,
            Executor defaultToolExecutionExecutor,
            ExecutionContextPropagation defaultExecutionContextPropagation) {
        this(properties, defaultModel, builtInToolRegistry, discoveredTools, discoveredHooks, List.of(), validator, defaultSessionManager, defaultRetryStrategy, objectMapper, defaultToolExecutionExecutor, defaultExecutionContextPropagation);
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            BuiltInToolRegistry builtInToolRegistry,
            List<DiscoveredTool> discoveredTools,
            List<HookProvider> discoveredHooks,
            List<Skill> discoveredSkills,
            Validator validator,
            SessionManager defaultSessionManager,
            ModelRetryStrategy defaultRetryStrategy,
            ObjectMapper objectMapper,
            Executor defaultToolExecutionExecutor) {
        this(properties, defaultModel, builtInToolRegistry, discoveredTools, discoveredHooks, discoveredSkills, validator, defaultSessionManager, defaultRetryStrategy, objectMapper, defaultToolExecutionExecutor, ExecutionContextPropagation.noop());
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            BuiltInToolRegistry builtInToolRegistry,
            List<DiscoveredTool> discoveredTools,
            List<HookProvider> discoveredHooks,
            List<Skill> discoveredSkills,
            Validator validator,
            SessionManager defaultSessionManager,
            ModelRetryStrategy defaultRetryStrategy,
            ObjectMapper objectMapper,
            Executor defaultToolExecutionExecutor,
            ExecutionContextPropagation defaultExecutionContextPropagation) {
        this.properties = properties;
        this.defaultModel = defaultModel;
        this.builtInTools = builtInToolRegistry == null ? List.of() : builtInToolRegistry.definitions();
        this.discoveredTools = List.copyOf(discoveredTools);
        this.discoveredHooks = List.copyOf(discoveredHooks);
        this.discoveredSkills = List.copyOf(discoveredSkills);
        this.validator = validator;
        this.defaultSessionManager = defaultSessionManager;
        this.defaultRetryStrategy = defaultRetryStrategy;
        this.objectMapper = objectMapper;
        this.defaultToolExecutionExecutor = defaultToolExecutionExecutor;
        this.defaultExecutionContextPropagation = defaultExecutionContextPropagation == null
                ? ExecutionContextPropagation.noop()
                : defaultExecutionContextPropagation;
    }

    public Builder builder() {
        return new Builder(
            resolveBuilderDefaults(null),
            builtInTools,
            discoveredTools,
            discoveredHooks,
            discoveredSkills,
            validator,
            defaultSessionManager,
            objectMapper,
            defaultToolExecutionExecutor,
            defaultExecutionContextPropagation);
    }

    public Builder builder(String name) {
        return new Builder(
            resolveBuilderDefaults(name),
            builtInTools,
            discoveredTools,
            discoveredHooks,
            discoveredSkills,
            validator,
            defaultSessionManager,
            objectMapper,
            defaultToolExecutionExecutor,
            defaultExecutionContextPropagation);
    }

    static Model createDefaultModel(ArachneProperties properties) {
        return createDefaultModel(properties.getModel());
    }

    static Model createDefaultModel(ArachneProperties.ModelProperties modelProperties) {
        String provider = modelProperties.getProvider();
        if (!"bedrock".equalsIgnoreCase(provider)) {
            throw new UnsupportedModelProviderException(provider);
        }

        BedrockModel.PromptCaching promptCaching = new BedrockModel.PromptCaching(
                modelProperties.getBedrock().getCache().isSystemPrompt(),
                modelProperties.getBedrock().getCache().isTools());

        String modelId = modelProperties.getId();
        String region = modelProperties.getRegion();
        if (modelId != null && !modelId.isBlank()) {
            return new BedrockModel(modelId, region, promptCaching);
        }
        if (region != null && !region.isBlank()) {
            return new BedrockModel(BedrockModel.DEFAULT_MODEL_ID, region, promptCaching);
        }
        return new BedrockModel(BedrockModel.DEFAULT_MODEL_ID, BedrockModel.DEFAULT_REGION, promptCaching);
    }

    private BuilderDefaults resolveBuilderDefaults(String name) {
        if (name == null || name.isBlank()) {
            return resolveDefaultBuilderDefaults();
        }

        return resolveNamedBuilderDefaults(requireNamedAgentProperties(name));
    }

    private BuilderDefaults resolveDefaultBuilderDefaults() {
        ArachneProperties.BuiltInToolProperties builtIns = properties.getAgent().getBuiltIns();
        return new BuilderDefaults(
                copyModelProperties(properties.getModel()),
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
        ResolvedModelDefaults resolvedModelDefaults = resolveNamedModelDefaults(namedProperties.getModel());
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

    private ResolvedModelDefaults resolveNamedModelDefaults(ArachneProperties.ModelOverrideProperties modelOverrides) {
        ArachneProperties.ModelProperties mergedModel = mergeModelProperties(properties.getModel(), modelOverrides);
        if (!hasModelOverride(modelOverrides)) {
            return new ResolvedModelDefaults(mergedModel, defaultModel);
        }
        return new ResolvedModelDefaults(mergedModel, createDefaultModel(mergedModel));
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
                || hasText(modelProperties.getRegion())
                || hasBedrockOverride(modelProperties.getBedrock()));
    }

    private static boolean hasBedrockOverride(ArachneProperties.BedrockOverrideProperties bedrockProperties) {
        return bedrockProperties != null
                && bedrockProperties.getCache() != null
                && (bedrockProperties.getCache().getSystemPrompt() != null
                || bedrockProperties.getCache().getTools() != null);
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
        if (overrides.getBedrock() != null && overrides.getBedrock().getCache() != null) {
            if (overrides.getBedrock().getCache().getSystemPrompt() != null) {
                merged.getBedrock().getCache().setSystemPrompt(overrides.getBedrock().getCache().getSystemPrompt());
            }
            if (overrides.getBedrock().getCache().getTools() != null) {
                merged.getBedrock().getCache().setTools(overrides.getBedrock().getCache().getTools());
            }
        }
        return merged;
    }

    private static ArachneProperties.ModelProperties copyModelProperties(ArachneProperties.ModelProperties source) {
        ArachneProperties.ModelProperties copy = new ArachneProperties.ModelProperties();
        copy.setProvider(source.getProvider());
        copy.setId(source.getId());
        copy.setRegion(source.getRegion());
        copy.getBedrock().getCache().setSystemPrompt(source.getBedrock().getCache().isSystemPrompt());
        copy.getBedrock().getCache().setTools(source.getBedrock().getCache().isTools());
        return copy;
    }

    private static Set<String> normalizeQualifiers(List<String> toolQualifiers) {
        return normalizeStrings(toolQualifiers);
    }

    private static Set<String> normalizeNames(List<String> values) {
        return normalizeStrings(values);
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

    private record BuilderDefaults(
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

            private record ResolvedModelDefaults(
                ArachneProperties.ModelProperties modelProperties,
                Model defaultModel) {
            }

            private record BuilderRuntime(
                Model model,
                List<Tool> tools,
                HookRegistry hooks,
                ConversationManager conversationManager,
                SessionManager sessionManager,
                EventLoop eventLoop) {
            }

    public static class Builder {

        private final BuilderDefaults defaults;
        private final Model defaultModel;
        private final List<BuiltInToolDefinition> builtInTools;
        private final List<DiscoveredTool> discoveredTools;
        private final List<HookProvider> discoveredHooks;
        private final List<Skill> discoveredSkills;
        private final Validator validator;
        private final SessionManager defaultSessionManager;
        private final ObjectMapper objectMapper;
        private final Executor defaultToolExecutionExecutor;
        private final ExecutionContextPropagation defaultExecutionContextPropagation;
        private Model model;
        private List<Tool> tools = List.of();
        private String systemPrompt;
        private ToolExecutionMode toolExecutionMode;
        private boolean useDiscoveredTools;
        private Set<String> toolQualifiers;
        private boolean inheritBuiltInTools;
        private Set<String> builtInToolNames;
        private Set<String> builtInToolGroups;
        private ConversationManager conversationManager;
        private SessionManager sessionManager;
        private ModelRetryStrategy retryStrategy;
        private String sessionId;
        private Executor toolExecutionExecutor;
        private ExecutionContextPropagation executionContextPropagation;
        private AgentState state = new AgentState();
        private List<HookProvider> hookProviders = List.of();
        private List<Plugin> plugins = List.of();
        private List<Skill> skills = List.of();

        private Builder(
                BuilderDefaults defaults,
            List<BuiltInToolDefinition> builtInTools,
                List<DiscoveredTool> discoveredTools,
                List<HookProvider> discoveredHooks,
                List<Skill> discoveredSkills,
                Validator validator,
                SessionManager defaultSessionManager,
                ObjectMapper objectMapper,
                Executor defaultToolExecutionExecutor,
                ExecutionContextPropagation defaultExecutionContextPropagation) {
            this.defaults = Objects.requireNonNull(defaults, "defaults must not be null");
            this.defaultModel = defaults.defaultModel();
            this.builtInTools = List.copyOf(builtInTools);
            this.discoveredTools = List.copyOf(discoveredTools);
            this.discoveredHooks = List.copyOf(discoveredHooks);
            this.discoveredSkills = List.copyOf(discoveredSkills);
            this.validator = validator;
            this.defaultSessionManager = defaultSessionManager;
            this.objectMapper = objectMapper;
            this.defaultToolExecutionExecutor = defaultToolExecutionExecutor;
            this.defaultExecutionContextPropagation = defaultExecutionContextPropagation;
            this.systemPrompt = defaults.systemPrompt();
            this.toolExecutionMode = defaults.toolExecutionMode();
            this.useDiscoveredTools = defaults.useDiscoveredTools();
            this.toolQualifiers = defaults.toolQualifiers();
            this.inheritBuiltInTools = defaults.inheritBuiltInTools();
            this.builtInToolNames = defaults.builtInToolNames();
            this.builtInToolGroups = defaults.builtInToolGroups();
            this.retryStrategy = defaults.retryStrategy();
            this.sessionId = defaults.sessionId();
            this.executionContextPropagation = defaultExecutionContextPropagation;
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

        public Builder steeringHandlers(SteeringHandler... steeringHandlers) {
            return steeringHandlers(List.of(steeringHandlers));
        }

        public Builder steeringHandlers(List<? extends SteeringHandler> steeringHandlers) {
            this.plugins = Stream.concat(this.plugins.stream(), steeringHandlers.stream().map(Plugin.class::cast)).toList();
            return this;
        }

        public Builder skills(Skill... skills) {
            this.skills = Stream.concat(this.skills.stream(), List.of(skills).stream()).toList();
            return this;
        }

        public Builder skills(List<? extends Skill> skills) {
            this.skills = Stream.concat(this.skills.stream(), skills.stream()).toList();
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

        public Builder executionContextPropagation(ExecutionContextPropagation executionContextPropagation) {
            this.executionContextPropagation = executionContextPropagation == null
                    ? ExecutionContextPropagation.noop()
                    : executionContextPropagation;
            return this;
        }

        public Builder useDiscoveredTools(boolean useDiscoveredTools) {
            this.useDiscoveredTools = useDiscoveredTools;
            return this;
        }

        public Builder inheritBuiltInTools(boolean inheritBuiltInTools) {
            this.inheritBuiltInTools = inheritBuiltInTools;
            return this;
        }

        public Builder builtInToolNames(String... builtInToolNames) {
            this.builtInToolNames = normalizeNames(List.of(builtInToolNames));
            return this;
        }

        public Builder builtInToolGroups(String... builtInToolGroups) {
            this.builtInToolGroups = normalizeNames(List.of(builtInToolGroups));
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
            List<Plugin> resolvedPlugins = resolvePlugins();
            BuilderRuntime runtime = resolveRuntime(resolvedPlugins);
            return createAgent(runtime);
        }

        private BuilderRuntime resolveRuntime(List<Plugin> resolvedPlugins) {
            HookRegistry hooks = resolveHooks(resolvedPlugins);
            return new BuilderRuntime(
                    wrapWithRetryIfNeeded(resolveModel()),
                    resolveTools(resolvedPlugins),
                    hooks,
                    resolveConversationManager(),
                    resolveSessionManager(),
                    createEventLoop(hooks));
        }

        private Agent createAgent(BuilderRuntime runtime) {
            return new DefaultAgent(
                    runtime.model(),
                    runtime.tools(),
                    runtime.eventLoop(),
                    runtime.hooks(),
                    systemPrompt,
                    validator,
                    objectMapper,
                    runtime.conversationManager(),
                    runtime.sessionManager(),
                    sessionId,
                    state);
        }

        private EventLoop createEventLoop(HookRegistry hooks) {
            return new EventLoop(
                    hooks,
                    new ToolExecutor(
                            toolExecutionMode,
                            resolveToolExecutionExecutor(),
                            resolveExecutionContextPropagation()));
        }

        private List<Tool> resolveTools(List<? extends Plugin> resolvedPlugins) {
            return Stream.concat(
                    Stream.concat(
                            Stream.concat(resolveBuiltInTools().stream(), resolveDiscoveredTools().stream()),
                            resolvePluginTools(resolvedPlugins).stream()),
                    tools.stream()).toList();
        }

        private List<Tool> resolveBuiltInTools() {
            return builtInTools.stream()
                    .filter(definition -> shouldIncludeBuiltIn(definition))
                    .map(BuiltInToolDefinition::tool)
                    .toList();
        }

        private boolean shouldIncludeBuiltIn(BuiltInToolDefinition definition) {
            boolean explicitlySelected = definition.matches(builtInToolNames, builtInToolGroups);
            return explicitlySelected || (inheritBuiltInTools && definition.includedByDefault());
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

        private List<Plugin> resolvePlugins() {
            ArrayList<Plugin> resolved = new ArrayList<>(plugins);
            List<Skill> resolvedSkills = resolveSkills();
            if (!resolvedSkills.isEmpty()) {
                resolved.add(new AgentSkillsPlugin(resolvedSkills));
            }
            return List.copyOf(resolved);
        }

        private List<Skill> resolveSkills() {
            LinkedHashSet<String> orderedNames = new LinkedHashSet<>();
            java.util.LinkedHashMap<String, Skill> resolved = new java.util.LinkedHashMap<>();
            for (Skill discoveredSkill : discoveredSkills) {
                resolved.put(discoveredSkill.name(), discoveredSkill);
                orderedNames.add(discoveredSkill.name());
            }
            for (Skill runtimeSkill : skills) {
                resolved.put(runtimeSkill.name(), runtimeSkill);
                orderedNames.add(runtimeSkill.name());
            }
            return orderedNames.stream()
                    .map(resolved::get)
                    .filter(Objects::nonNull)
                    .toList();
        }

        private List<Tool> resolvePluginTools(List<? extends Plugin> resolvedPlugins) {
            return resolvedPlugins.stream()
                    .flatMap(plugin -> plugin.tools().stream())
                    .toList();
        }

        private HookRegistry resolveHooks(List<? extends Plugin> resolvedPlugins) {
            List<HookProvider> resolvedHookProviders = Stream.concat(
                    Stream.concat(discoveredHooks.stream(), hookProviders.stream()),
                    resolvedPlugins.stream())
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

        private ExecutionContextPropagation resolveExecutionContextPropagation() {
            if (executionContextPropagation != null) {
                return executionContextPropagation;
            }
            return defaultExecutionContextPropagation == null
                    ? ExecutionContextPropagation.noop()
                    : defaultExecutionContextPropagation;
        }
    }
}
