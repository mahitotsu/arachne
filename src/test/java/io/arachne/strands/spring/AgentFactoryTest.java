package io.arachne.strands.spring;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.session.MapSessionRepository;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.hooks.Plugin;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.ModelThrottledException;
import io.arachne.strands.model.bedrock.BedrockModel;
import io.arachne.strands.model.retry.ExponentialBackoffRetryStrategy;
import io.arachne.strands.session.SpringSessionManager;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolResult;
import io.arachne.strands.tool.annotation.DiscoveredTool;
import io.arachne.strands.tool.annotation.StrandsTool;

class AgentFactoryTest {

    @Test
    void buildFallsBackToConfiguredBedrockModel() {
        ArachneProperties properties = new ArachneProperties();
        properties.getModel().setId("jp.amazon.nova-2-lite-v1:0");
        properties.getModel().setRegion("ap-northeast-1");

        Agent agent = new AgentFactory(properties).builder().build();

        assertThat(agent.getModel()).isInstanceOf(BedrockModel.class);
        assertThat(((BedrockModel) agent.getModel()).getModelId()).isEqualTo("jp.amazon.nova-2-lite-v1:0");
        assertThat(((BedrockModel) agent.getModel()).getRegion()).isEqualTo("ap-northeast-1");
    }

    @Test
    void buildPrefersInjectedDefaultModelBean() {
        ArachneProperties properties = new ArachneProperties();
        Model model = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("ok"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));

        Agent agent = new AgentFactory(properties, model).builder().build();

        assertThat(agent.getModel()).isSameAs(model);
    }

    @Test
    void buildIncludesDiscoveredAnnotationTools() {
        ArachneProperties properties = new ArachneProperties();
        Model model = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("ok"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        DiscoveredTool discoveredTool = new io.arachne.strands.tool.annotation.AnnotationToolScanner()
            .scanDiscoveredTools(List.of(new ToolBean()))
                .getFirst();

        Agent agent = new AgentFactory(properties, model, List.of(discoveredTool)).builder().build();

        assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).contains("helloTool");
    }

    @Test
    void buildCanRegisterPluginToolsAndHooks() {
        ArachneProperties properties = new ArachneProperties();
        Model model = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("ok"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        Plugin plugin = new Plugin() {
            @Override
            public List<Tool> tools() {
                return List.of(new Tool() {
                    @Override
                    public io.arachne.strands.model.ToolSpec spec() {
                        return new io.arachne.strands.model.ToolSpec("pluginTool", "plugin", null);
                    }

                    @Override
                    public ToolResult invoke(Object input) {
                        return ToolResult.success(null, "plugin");
                    }
                });
            }

            @Override
            public void registerHooks(io.arachne.strands.hooks.HookRegistrar registrar) {
                registrar.beforeInvocation(event -> event.state().put("source", "plugin"));
            }
        };

        Agent agent = new AgentFactory(properties, model)
                .builder()
                .plugins(plugin)
                .build();

        agent.run("hello");

        assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).contains("pluginTool");
        assertThat(agent.getState().get("source")).isEqualTo("plugin");
    }

        @Test
        void buildCanFilterDiscoveredToolsByQualifier() {
        ArachneProperties properties = new ArachneProperties();
        Model model = (messages, tools) -> List.of(
            new ModelEvent.TextDelta("ok"),
            new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        List<DiscoveredTool> discoveredTools = new io.arachne.strands.tool.annotation.AnnotationToolScanner()
            .scanDiscoveredTools(List.of(new PlannerToolBean(), new SupportToolBean()));

        Agent agent = new AgentFactory(properties, model, discoveredTools)
            .builder()
            .toolQualifiers("planner")
            .build();

        assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).containsExactly("plannerTool");
        }

        @Test
        void buildCanDisableDiscoveredTools() {
        ArachneProperties properties = new ArachneProperties();
        Model model = (messages, tools) -> List.of(
            new ModelEvent.TextDelta("ok"),
            new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        List<DiscoveredTool> discoveredTools = new io.arachne.strands.tool.annotation.AnnotationToolScanner()
            .scanDiscoveredTools(List.of(new PlannerToolBean()));

        Agent agent = new AgentFactory(properties, model, discoveredTools)
            .builder()
            .useDiscoveredTools(false)
            .build();

        assertThat(agent.getTools()).isEmpty();
        }

    @Test
    void buildUsesConfiguredSessionIdAndRestoresAcrossAgents() {
        ArachneProperties properties = new ArachneProperties();
        properties.getAgent().getSession().setId("shared-session");
        Model model = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("ok"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        SpringSessionManager sessionManager = new SpringSessionManager(
            new MapSessionRepository(new java.util.concurrent.ConcurrentHashMap<>()));

        AgentFactory factory = new AgentFactory(properties, model, List.of(), io.arachne.strands.tool.BeanValidationSupport.defaultValidator(), sessionManager);
        Agent first = factory.builder().build();
        first.getState().put("topic", "travel");
        first.run("hello");

        Agent restored = factory.builder().build();

        assertThat(restored.getMessages()).hasSize(2);
        assertThat(restored.getState().get("topic")).isEqualTo("travel");
    }

    @Test
    void buildCreatesIndependentRuntimeInstances() {
        ArachneProperties properties = new ArachneProperties();
        AgentFactory factory = new AgentFactory(properties, echoModel());

        Agent first = factory.builder().build();
        Agent second = factory.builder().build();

        first.getState().put("topic", "travel");
        first.run("first");
        second.run("second");

        assertThat(first.getMessages()).hasSize(2);
        assertThat(second.getMessages()).hasSize(2);
        assertThat(first.getMessages().getFirst().content().getFirst())
                .isEqualTo(io.arachne.strands.types.ContentBlock.text("first"));
        assertThat(second.getMessages().getFirst().content().getFirst())
                .isEqualTo(io.arachne.strands.types.ContentBlock.text("second"));
        assertThat(second.getState().get("topic")).isNull();
    }

    @Test
    void buildCanApplyExplicitRetryStrategy() {
        ArachneProperties properties = new ArachneProperties();
        AtomicInteger calls = new AtomicInteger();
        Model model = (messages, tools) -> {
            if (calls.incrementAndGet() < 3) {
                throw new ModelThrottledException("retry me");
            }
            return List.of(
                    new ModelEvent.TextDelta("ok"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        };

        Agent agent = new AgentFactory(properties, model)
                .builder()
                .retryStrategy(new ExponentialBackoffRetryStrategy(4, Duration.ZERO, Duration.ZERO))
                .build();

        assertThat(agent.run("hello").text()).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void buildNamedAgentUsesNamedModelOverrides() {
        ArachneProperties properties = new ArachneProperties();
        properties.getModel().setId("jp.amazon.nova-2-lite-v1:0");
        properties.getModel().setRegion("ap-northeast-1");

        ArachneProperties.NamedAgentProperties analyst = new ArachneProperties.NamedAgentProperties();
        analyst.getModel().setId("us.amazon.nova-pro-v1:0");
        analyst.getModel().setRegion("us-east-1");
        properties.getAgents().put("analyst", analyst);

        Model sharedModel = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("shared"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));

        Agent agent = new AgentFactory(properties, sharedModel).builder("analyst").build();

        assertThat(agent.getModel()).isInstanceOf(BedrockModel.class);
        assertThat(((BedrockModel) agent.getModel()).getModelId()).isEqualTo("us.amazon.nova-pro-v1:0");
        assertThat(((BedrockModel) agent.getModel()).getRegion()).isEqualTo("us-east-1");
    }

    @Test
    void buildNamedAgentUsesNamedToolQualifiersAndSessionId() {
        ArachneProperties properties = new ArachneProperties();
        properties.getAgent().getSession().setId("global-session");

        ArachneProperties.NamedAgentProperties planner = new ArachneProperties.NamedAgentProperties();
        planner.setToolQualifiers(List.of("planner"));
        planner.getSession().setId("planner-session");
        properties.getAgents().put("planner", planner);

        Model model = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("ok"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        List<DiscoveredTool> discoveredTools = new io.arachne.strands.tool.annotation.AnnotationToolScanner()
                .scanDiscoveredTools(List.of(new PlannerToolBean(), new SupportToolBean()));
        SpringSessionManager sessionManager = new SpringSessionManager(
                new MapSessionRepository(new java.util.concurrent.ConcurrentHashMap<>()));

        AgentFactory factory = new AgentFactory(
                properties,
                model,
                discoveredTools,
                io.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                sessionManager);

        Agent first = factory.builder("planner").build();
        first.getState().put("topic", "travel");
        first.run("hello");

        Agent restored = factory.builder("planner").build();

        assertThat(restored.getTools()).extracting(tool -> tool.spec().name()).containsExactly("plannerTool");
        assertThat(restored.getMessages()).hasSize(2);
        assertThat(restored.getState().get("topic")).isEqualTo("travel");
    }

    @Test
    void buildNamedAgentCanEnableRetryWithoutGlobalRetry() {
        ArachneProperties properties = new ArachneProperties();

        ArachneProperties.NamedAgentProperties resilient = new ArachneProperties.NamedAgentProperties();
        resilient.getRetry().setEnabled(true);
        resilient.getRetry().setMaxAttempts(4);
        resilient.getRetry().setInitialDelay(Duration.ZERO);
        resilient.getRetry().setMaxDelay(Duration.ZERO);
        properties.getAgents().put("resilient", resilient);

        AtomicInteger calls = new AtomicInteger();
        Model model = (messages, tools) -> {
            if (calls.incrementAndGet() < 3) {
                throw new ModelThrottledException("retry me");
            }
            return List.of(
                    new ModelEvent.TextDelta("ok"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        };

        Agent agent = new AgentFactory(properties, model).builder("resilient").build();

        assertThat(agent.run("hello").text()).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void buildNamedAgentCanDisableGlobalRetry() {
        ArachneProperties properties = new ArachneProperties();

        ArachneProperties.NamedAgentProperties nonRetrying = new ArachneProperties.NamedAgentProperties();
        nonRetrying.getRetry().setEnabled(false);
        properties.getAgents().put("nonRetrying", nonRetrying);

        AtomicInteger calls = new AtomicInteger();
        Model model = (messages, tools) -> {
            calls.incrementAndGet();
            throw new ModelThrottledException("retry me");
        };

        AgentFactory factory = new AgentFactory(
                properties,
                model,
                List.of(),
                io.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                null,
                new ExponentialBackoffRetryStrategy(4, Duration.ZERO, Duration.ZERO));

        Agent agent = factory.builder("nonRetrying").build();

        assertThatThrownBy(() -> agent.run("hello"))
                .isInstanceOf(ModelThrottledException.class)
                .hasMessageContaining("retry me");
        assertThat(calls).hasValue(1);
    }

    @Test
    void buildNamedAgentRejectsUnknownConfiguration() {
        ArachneProperties properties = new ArachneProperties();

        AgentFactory factory = new AgentFactory(properties);

        assertThatThrownBy(() -> factory.builder("missing"))
                .isInstanceOf(NamedAgentNotFoundException.class)
                .hasMessageContaining("missing");
    }

    static class ToolBean {

        @StrandsTool
        public String helloTool() {
            return "ok";
        }
    }

    static class PlannerToolBean {

        @StrandsTool(qualifiers = "planner")
        public String plannerTool() {
            return "planner";
        }
    }

    static class SupportToolBean {

        @StrandsTool(qualifiers = "support")
        public String supportTool() {
            return "support";
        }
    }

    private static Model echoModel() {
        return (messages, tools) -> {
            String prompt = messages.getLast().content().stream()
                    .filter(io.arachne.strands.types.ContentBlock.Text.class::isInstance)
                    .map(io.arachne.strands.types.ContentBlock.Text.class::cast)
                    .map(io.arachne.strands.types.ContentBlock.Text::text)
                    .findFirst()
                    .orElse("(missing prompt)");
            return List.of(
                    new ModelEvent.TextDelta("Echo: " + prompt),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        };
    }
}