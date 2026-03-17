package io.arachne.strands.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Arachne Strands.
 *
 * <pre>
 * arachne:
 *   strands:
 *     model:
 *       provider: bedrock          # bedrock | openai
 *       id: us.amazon.nova-pro-v1:0
 *     agent:
 *       system-prompt: "You are a helpful assistant."
 * </pre>
 */
@ConfigurationProperties(prefix = "arachne.strands")
public class ArachneProperties {

    private ModelProperties model = new ModelProperties();
    private AgentProperties agent = new AgentProperties();

    public ModelProperties getModel() { return model; }
    public void setModel(ModelProperties model) { this.model = model; }

    public AgentProperties getAgent() { return agent; }
    public void setAgent(AgentProperties agent) { this.agent = agent; }

    public static class ModelProperties {
        private String provider = "bedrock";
        private String id;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class AgentProperties {
        private String systemPrompt;

        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    }
}
