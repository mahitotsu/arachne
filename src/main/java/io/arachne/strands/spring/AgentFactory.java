package io.arachne.strands.spring;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.validation.Validator;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.agent.DefaultAgent;
import io.arachne.strands.eventloop.EventLoop;
import io.arachne.strands.hooks.NoOpHookRegistry;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.bedrock.BedrockModel;
import io.arachne.strands.tool.BeanValidationSupport;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolExecutionMode;
import io.arachne.strands.tool.ToolExecutor;
import io.arachne.strands.tool.annotation.DiscoveredTool;

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
    private final Validator validator;

    public AgentFactory(ArachneProperties properties) {
        this(properties, null, List.of(), BeanValidationSupport.defaultValidator());
    }

    public AgentFactory(ArachneProperties properties, Model defaultModel) {
        this(properties, defaultModel, List.of(), BeanValidationSupport.defaultValidator());
    }

    public AgentFactory(ArachneProperties properties, Model defaultModel, List<DiscoveredTool> discoveredTools) {
        this(properties, defaultModel, discoveredTools, BeanValidationSupport.defaultValidator());
    }

    public AgentFactory(
            ArachneProperties properties,
            Model defaultModel,
            List<DiscoveredTool> discoveredTools,
            Validator validator) {
        this.properties = properties;
        this.defaultModel = defaultModel;
        this.discoveredTools = List.copyOf(discoveredTools);
        this.validator = validator;
    }

    public Builder builder() {
        return new Builder(properties, defaultModel, discoveredTools, validator);
    }

    static Model createDefaultModel(ArachneProperties properties) {
        ArachneProperties.ModelProperties modelProperties = properties.getModel();
        String provider = modelProperties.getProvider();
        if (!"bedrock".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("Unsupported model provider for Phase 1: " + provider);
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

    public static class Builder {

        private final ArachneProperties properties;
        private final Model defaultModel;
        private final List<DiscoveredTool> discoveredTools;
        private final Validator validator;
        private Model model;
        private List<Tool> tools = List.of();
        private String systemPrompt;
        private ToolExecutionMode toolExecutionMode = ToolExecutionMode.PARALLEL;
        private boolean useDiscoveredTools = true;
        private Set<String> toolQualifiers = Set.of();

        private Builder(
                ArachneProperties properties,
                Model defaultModel,
                List<DiscoveredTool> discoveredTools,
                Validator validator) {
            this.properties = properties;
            this.defaultModel = defaultModel;
            this.discoveredTools = List.copyOf(discoveredTools);
            this.validator = validator;
            this.systemPrompt = properties.getAgent().getSystemPrompt();
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

        public Builder toolExecutionMode(ToolExecutionMode toolExecutionMode) {
            this.toolExecutionMode = toolExecutionMode;
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

        public Agent build() {
            Model resolvedModel = resolveModel();
            NoOpHookRegistry hooks = new NoOpHookRegistry();
            EventLoop eventLoop = new EventLoop(hooks, new ToolExecutor(toolExecutionMode));
            List<Tool> resolvedTools = Stream.concat(resolveDiscoveredTools().stream(), tools.stream()).toList();
            return new DefaultAgent(resolvedModel, resolvedTools, eventLoop, hooks, systemPrompt, validator);
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

        private Model resolveModel() {
            if (model != null) {
                return model;
            }
            if (defaultModel != null) {
                return defaultModel;
            }
            return createDefaultModel(properties);
        }
    }
}
