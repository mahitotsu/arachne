package com.mahitotsu.arachne.strands.spring;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahitotsu.arachne.strands.agent.Agent;
import com.mahitotsu.arachne.strands.agent.AgentState;
import com.mahitotsu.arachne.strands.agent.DefaultAgent;
import com.mahitotsu.arachne.strands.agent.conversation.ConversationManager;
import com.mahitotsu.arachne.strands.agent.conversation.SlidingWindowConversationManager;
import com.mahitotsu.arachne.strands.eventloop.EventLoop;
import com.mahitotsu.arachne.strands.hooks.DispatchingHookRegistry;
import com.mahitotsu.arachne.strands.hooks.HookProvider;
import com.mahitotsu.arachne.strands.hooks.HookRegistry;
import com.mahitotsu.arachne.strands.hooks.NoOpHookRegistry;
import com.mahitotsu.arachne.strands.hooks.Plugin;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.retry.ModelRetryStrategy;
import com.mahitotsu.arachne.strands.model.retry.RetryingModel;
import com.mahitotsu.arachne.strands.session.SessionManager;
import com.mahitotsu.arachne.strands.skills.AgentSkillsPlugin;
import com.mahitotsu.arachne.strands.skills.Skill;
import com.mahitotsu.arachne.strands.steering.SteeringHandler;
import com.mahitotsu.arachne.strands.tool.BeanValidationSupport;
import com.mahitotsu.arachne.strands.tool.ExecutionContextPropagation;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolExecutionMode;
import com.mahitotsu.arachne.strands.tool.ToolExecutor;
import com.mahitotsu.arachne.strands.tool.annotation.DiscoveredTool;

import jakarta.validation.Validator;

/**
 * Factory for creating {@link Agent} instances.
 * Injected automatically by {@link ArachneAutoConfiguration}.
 *
 * <p>Spring auto-configuration contributes shared beans such as the default {@link Model},
 * discovered tools, hooks, skills, retry strategy, sessions, and executor infrastructure.
 * This factory resolves named-agent defaults from those shared definitions and turns them
 * into runtime-local {@link Agent} instances through {@link Builder}.
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

    private final List<BuiltInToolDefinition> builtInTools;
    private final List<DiscoveredTool> discoveredTools;
    private final List<HookProvider> discoveredHooks;
    private final List<Skill> discoveredSkills;
    private final Validator validator;
    private final SessionManager defaultSessionManager;
    private final ObjectMapper objectMapper;
    private final Executor defaultToolExecutionExecutor;
    private final ExecutionContextPropagation defaultExecutionContextPropagation;
    private final AgentFactoryDefaultsResolver defaultsResolver;

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
        this.builtInTools = builtInToolRegistry == null ? List.of() : builtInToolRegistry.definitions();
        this.discoveredTools = List.copyOf(discoveredTools);
        this.discoveredHooks = List.copyOf(discoveredHooks);
        this.discoveredSkills = List.copyOf(discoveredSkills);
        this.validator = validator;
        this.defaultSessionManager = defaultSessionManager;
        this.objectMapper = objectMapper;
        this.defaultToolExecutionExecutor = defaultToolExecutionExecutor;
        this.defaultExecutionContextPropagation = defaultExecutionContextPropagation == null
                ? ExecutionContextPropagation.noop()
                : defaultExecutionContextPropagation;
        this.defaultsResolver = new AgentFactoryDefaultsResolver(properties, defaultModel, defaultRetryStrategy);
    }

    public Builder builder() {
        return createBuilder(resolveBuilderDefaults(null));
    }

    public Builder builder(String name) {
        return createBuilder(resolveBuilderDefaults(name));
    }

    private AgentFactoryDefaultsResolver.BuilderDefaults resolveBuilderDefaults(String name) {
        return defaultsResolver.resolve(name);
    }

    private Builder createBuilder(AgentFactoryDefaultsResolver.BuilderDefaults defaults) {
        return new Builder(
                defaults,
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
        return AgentFactoryModelResolver.createDefaultModel(properties);
    }

    static Model createDefaultModel(ArachneProperties.ModelProperties modelProperties) {
        return AgentFactoryModelResolver.createDefaultModel(modelProperties);
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

        private final AgentFactoryDefaultsResolver.BuilderDefaults defaults;
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
        private Class<?> structuredOutputType;
        private String structuredOutputPrompt;

        private Builder(
                AgentFactoryDefaultsResolver.BuilderDefaults defaults,
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
            this.builtInToolNames = AgentFactoryDefaultsResolver.normalizeNames(List.of(builtInToolNames));
            return this;
        }

        public Builder builtInToolGroups(String... builtInToolGroups) {
            this.builtInToolGroups = AgentFactoryDefaultsResolver.normalizeNames(List.of(builtInToolGroups));
            return this;
        }

        public Builder toolQualifiers(String... toolQualifiers) {
            this.toolQualifiers = AgentFactoryDefaultsResolver.normalizeQualifiers(List.of(toolQualifiers));
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

        public Builder structuredOutputType(Class<?> structuredOutputType) {
            this.structuredOutputType = structuredOutputType;
            return this;
        }

        public Builder structuredOutputPrompt(String structuredOutputPrompt) {
            this.structuredOutputPrompt = structuredOutputPrompt;
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
                    state,
                    structuredOutputType,
                    structuredOutputPrompt);
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
            return AgentFactoryModelResolver.createDefaultModel(defaults.modelProperties());
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
