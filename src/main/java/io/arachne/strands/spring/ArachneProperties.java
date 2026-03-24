package io.arachne.strands.spring;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.arachne.strands.tool.ToolExecutionMode;

/**
 * Configuration properties for Arachne Strands.
 *
 * <pre>
 * arachne:
 *   strands:
 *     model:
 *       provider: bedrock          # bedrock | openai
 *       id: jp.amazon.nova-2-lite-v1:0
 *       region: ap-northeast-1
 *     agent:
 *       system-prompt: "You are a helpful assistant."
 * </pre>
 */
@ConfigurationProperties(prefix = "arachne.strands")
public class ArachneProperties {

    private ModelProperties model = new ModelProperties();
    private AgentProperties agent = new AgentProperties();
    private Map<String, NamedAgentProperties> agents = new LinkedHashMap<>();

    public ModelProperties getModel() {
        return model;
    }

    public void setModel(ModelProperties model) {
        this.model = model;
    }

    public AgentProperties getAgent() {
        return agent;
    }

    public void setAgent(AgentProperties agent) {
        this.agent = agent;
    }

    public Map<String, NamedAgentProperties> getAgents() {
        return agents;
    }

    public void setAgents(Map<String, NamedAgentProperties> agents) {
        this.agents = agents;
    }

    public static class ModelProperties {
        private String provider = "bedrock";
        private String id;
        private String region;
        private BedrockProperties bedrock = new BedrockProperties();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public BedrockProperties getBedrock() {
            return bedrock;
        }

        public void setBedrock(BedrockProperties bedrock) {
            this.bedrock = bedrock;
        }
    }

    public static class AgentProperties {
        private String systemPrompt;
        private ConversationProperties conversation = new ConversationProperties();
        private SessionProperties session = new SessionProperties();
        private RetryProperties retry = new RetryProperties();

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public ConversationProperties getConversation() {
            return conversation;
        }

        public void setConversation(ConversationProperties conversation) {
            this.conversation = conversation;
        }

        public SessionProperties getSession() {
            return session;
        }

        public void setSession(SessionProperties session) {
            this.session = session;
        }

        public RetryProperties getRetry() {
            return retry;
        }

        public void setRetry(RetryProperties retry) {
            this.retry = retry;
        }
    }

    public static class NamedAgentProperties {
        private ModelOverrideProperties model = new ModelOverrideProperties();
        private String systemPrompt;
        private ConversationOverrideProperties conversation = new ConversationOverrideProperties();
        private SessionOverrideProperties session = new SessionOverrideProperties();
        private RetryOverrideProperties retry = new RetryOverrideProperties();
        private Boolean useDiscoveredTools;
        private List<String> toolQualifiers = List.of();
        private ToolExecutionMode toolExecutionMode;

        public ModelOverrideProperties getModel() {
            return model;
        }

        public void setModel(ModelOverrideProperties model) {
            this.model = model;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public ConversationOverrideProperties getConversation() {
            return conversation;
        }

        public void setConversation(ConversationOverrideProperties conversation) {
            this.conversation = conversation;
        }

        public SessionOverrideProperties getSession() {
            return session;
        }

        public void setSession(SessionOverrideProperties session) {
            this.session = session;
        }

        public RetryOverrideProperties getRetry() {
            return retry;
        }

        public void setRetry(RetryOverrideProperties retry) {
            this.retry = retry;
        }

        public Boolean getUseDiscoveredTools() {
            return useDiscoveredTools;
        }

        public void setUseDiscoveredTools(Boolean useDiscoveredTools) {
            this.useDiscoveredTools = useDiscoveredTools;
        }

        public List<String> getToolQualifiers() {
            return toolQualifiers;
        }

        public void setToolQualifiers(List<String> toolQualifiers) {
            this.toolQualifiers = toolQualifiers;
        }

        public ToolExecutionMode getToolExecutionMode() {
            return toolExecutionMode;
        }

        public void setToolExecutionMode(ToolExecutionMode toolExecutionMode) {
            this.toolExecutionMode = toolExecutionMode;
        }
    }

    public static class ConversationProperties {
        private int windowSize = 40;

        public int getWindowSize() {
            return windowSize;
        }

        public void setWindowSize(int windowSize) {
            this.windowSize = windowSize;
        }
    }

    public static class ConversationOverrideProperties {
        private Integer windowSize;

        public Integer getWindowSize() {
            return windowSize;
        }

        public void setWindowSize(Integer windowSize) {
            this.windowSize = windowSize;
        }
    }

    public static class SessionProperties {
        private String id;
        private FileProperties file = new FileProperties();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public FileProperties getFile() {
            return file;
        }

        public void setFile(FileProperties file) {
            this.file = file;
        }
    }

    public static class SessionOverrideProperties {
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static class RetryProperties {
        private boolean enabled;
        private int maxAttempts = 6;
        private Duration initialDelay = Duration.ofSeconds(4);
        private Duration maxDelay = Duration.ofSeconds(240);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
        }
    }

    public static class RetryOverrideProperties {
        private Boolean enabled;
        private Integer maxAttempts;
        private Duration initialDelay;
        private Duration maxDelay;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(Integer maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
        }
    }

    public static class ModelOverrideProperties {
        private String provider;
        private String id;
        private String region;
        private BedrockOverrideProperties bedrock = new BedrockOverrideProperties();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public BedrockOverrideProperties getBedrock() {
            return bedrock;
        }

        public void setBedrock(BedrockOverrideProperties bedrock) {
            this.bedrock = bedrock;
        }
    }

    public static class BedrockProperties {
        private CacheProperties cache = new CacheProperties();

        public CacheProperties getCache() {
            return cache;
        }

        public void setCache(CacheProperties cache) {
            this.cache = cache;
        }
    }

    public static class BedrockOverrideProperties {
        private CacheOverrideProperties cache = new CacheOverrideProperties();

        public CacheOverrideProperties getCache() {
            return cache;
        }

        public void setCache(CacheOverrideProperties cache) {
            this.cache = cache;
        }
    }

    public static class CacheProperties {
        private boolean systemPrompt;
        private boolean tools;

        public boolean isSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(boolean systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public boolean isTools() {
            return tools;
        }

        public void setTools(boolean tools) {
            this.tools = tools;
        }
    }

    public static class CacheOverrideProperties {
        private Boolean systemPrompt;
        private Boolean tools;

        public Boolean getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(Boolean systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public Boolean getTools() {
            return tools;
        }

        public void setTools(Boolean tools) {
            this.tools = tools;
        }
    }

    public static class FileProperties {
        private String directory;

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }
    }
}
