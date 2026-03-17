package io.arachne.strands.spring;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.model.Model;
import io.arachne.strands.tool.Tool;

import java.util.List;

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

    public AgentFactory(ArachneProperties properties) {
        this.properties = properties;
    }

    public Builder builder() {
        return new Builder(properties);
    }

    public static class Builder {

        private final ArachneProperties properties;
        private Model model;
        private List<Tool> tools = List.of();
        private String systemPrompt;

        private Builder(ArachneProperties properties) {
            this.properties = properties;
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
            if (model == null) {
                throw new IllegalStateException(
                        "A Model must be provided. Set arachne.strands.model.provider or supply a Model bean.");
            }
            // TODO: replace with DefaultAgent once implemented
            throw new UnsupportedOperationException("DefaultAgent not yet implemented");
        }
    }
}
