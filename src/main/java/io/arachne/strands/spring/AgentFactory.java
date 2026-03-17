package io.arachne.strands.spring;

import java.util.List;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.agent.DefaultAgent;
import io.arachne.strands.eventloop.EventLoop;
import io.arachne.strands.hooks.NoOpHookRegistry;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.bedrock.BedrockModel;
import io.arachne.strands.tool.Tool;

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

    public AgentFactory(ArachneProperties properties) {
        this(properties, null);
    }

    public AgentFactory(ArachneProperties properties, Model defaultModel) {
        this.properties = properties;
        this.defaultModel = defaultModel;
    }

    public Builder builder() {
        return new Builder(properties, defaultModel);
    }

    static Model createDefaultModel(ArachneProperties properties) {
        String provider = properties.getModel().getProvider();
        if (!"bedrock".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("Unsupported model provider for Phase 1: " + provider);
        }

        String modelId = properties.getModel().getId();
        String region = properties.getModel().getRegion();
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
        private Model model;
        private List<Tool> tools = List.of();
        private String systemPrompt;

        private Builder(ArachneProperties properties, Model defaultModel) {
            this.properties = properties;
            this.defaultModel = defaultModel;
            this.systemPrompt = properties.getAgent().getSystemPrompt();
        }

        public Builder model(Model model) {
            this.model = model;
            return this;
        }

        public Builder tools(Tool... tools) {
            this.tools = List.of(tools);
            return this;
        }

        public Builder tools(List<Tool> tools) {
            this.tools = List.copyOf(tools);
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Agent build() {
            Model resolvedModel = model != null ? model : (defaultModel != null ? defaultModel : createDefaultModel(properties));
            NoOpHookRegistry hooks = new NoOpHookRegistry();
            EventLoop eventLoop = new EventLoop(hooks);
            return new DefaultAgent(resolvedModel, tools, eventLoop, hooks, systemPrompt);
        }
    }
}
