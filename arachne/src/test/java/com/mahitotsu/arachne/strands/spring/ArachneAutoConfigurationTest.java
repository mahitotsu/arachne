package com.mahitotsu.arachne.strands.spring;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import com.mahitotsu.arachne.strands.agent.Agent;
import com.mahitotsu.arachne.strands.hooks.HookProvider;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ModelThrottledException;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.model.bedrock.BedrockModel;
import com.mahitotsu.arachne.strands.model.retry.ModelRetryStrategy;
import com.mahitotsu.arachne.strands.session.FileSessionManager;
import com.mahitotsu.arachne.strands.session.SessionManager;
import com.mahitotsu.arachne.strands.session.SpringSessionManager;
import com.mahitotsu.arachne.strands.skills.Skill;
import com.mahitotsu.arachne.strands.tool.ExecutionContextPropagation;
import com.mahitotsu.arachne.strands.tool.annotation.StrandsTool;
import com.mahitotsu.arachne.strands.tool.annotation.ToolParam;
import com.mahitotsu.arachne.strands.types.Message;

class ArachneAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ArachneAutoConfiguration.class));

    @Test
    void autoConfigurationProvidesDefaultBedrockModel() {
        contextRunner
                .withPropertyValues(
                        "arachne.strands.model.id=jp.amazon.nova-2-lite-v1:0",
                        "arachne.strands.model.region=ap-northeast-1")
                .run(context -> {
                    assertThat(context).hasSingleBean(Model.class);
                    assertThat(context).hasSingleBean(AgentFactory.class);

                    Agent agent = context.getBean(AgentFactory.class).builder().build();
                    assertThat(agent.getModel()).isInstanceOf(BedrockModel.class);
                    assertThat(((BedrockModel) agent.getModel()).getModelId()).isEqualTo("jp.amazon.nova-2-lite-v1:0");
                    assertThat(((BedrockModel) agent.getModel()).getRegion()).isEqualTo("ap-northeast-1");
                });
    }

    @Test
    void autoConfigurationAppliesBedrockPromptCachingProperties() {
        contextRunner
                .withPropertyValues(
                        "arachne.strands.model.id=jp.amazon.nova-2-lite-v1:0",
                        "arachne.strands.model.bedrock.cache.system-prompt=true",
                        "arachne.strands.model.bedrock.cache.tools=true")
                .run(context -> {
                    Agent agent = context.getBean(AgentFactory.class).builder().build();

                    assertThat(agent.getModel()).isInstanceOf(BedrockModel.class);
                    assertThat(((BedrockModel) agent.getModel()).getPromptCaching())
                            .isEqualTo(new BedrockModel.PromptCaching(true, true));
                });
    }

    @Test
    void userModelBeanOverridesAutoConfiguredBedrockModel() {
        contextRunner
                .withUserConfiguration(CustomModelConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(Model.class);
                    assertThat(context.getBean(Model.class)).isSameAs(context.getBean("customModel"));
                });
    }

    @Test
    void autoConfigurationDiscoversAnnotatedToolBeans() {
        contextRunner
                .withUserConfiguration(AnnotatedToolConfiguration.class, CustomModelConfiguration.class)
                .run(context -> {
                    Agent agent = context.getBean(AgentFactory.class).builder().build();

                assertThat(agent.getTools()).extracting(tool -> tool.spec().name())
                    .contains("weather", "calculator", "current_time", "resource_reader", "resource_list");
                });
    }

        @Test
        void autoConfigurationCanDisableBuiltInInheritancePerNamedAgent() {
        contextRunner
            .withPropertyValues("arachne.strands.agents.planner.built-ins.inherit-defaults=false")
            .withUserConfiguration(CustomModelConfiguration.class)
            .run(context -> {
                Agent agent = context.getBean(AgentFactory.class).builder("planner").build();

                assertThat(agent.getTools()).extracting(tool -> tool.spec().name())
                    .doesNotContain("calculator", "current_time", "resource_reader", "resource_list");
            });
        }

        @Test
        void autoConfigurationCanSelectBuiltInToolGroupsPerNamedAgent() {
        contextRunner
            .withPropertyValues(
                "arachne.strands.agents.reader.built-ins.inherit-defaults=false",
                "arachne.strands.agents.reader.built-ins.tool-groups[0]=resource")
            .withUserConfiguration(CustomModelConfiguration.class)
            .run(context -> {
                Agent agent = context.getBean(AgentFactory.class).builder("reader").build();

                assertThat(agent.getTools()).extracting(tool -> tool.spec().name())
                    .contains("resource_reader", "resource_list")
                    .doesNotContain("calculator", "current_time");
            });
        }

    @Test
    void autoConfigurationDiscoversAnnotatedHookBeans() {
        contextRunner
                .withUserConfiguration(AnnotatedHookConfiguration.class, CustomModelConfiguration.class)
                .run(context -> {
                    Agent agent = context.getBean(AgentFactory.class).builder().build();

                    assertThat(agent.run("hello").text()).isEqualTo("ok-hooked");
                    assertThat(agent.getState().get("hooked")).isEqualTo(Boolean.TRUE);
                });
    }

    @Test
    void autoConfigurationWiresDiscoveredSkillsIntoBuiltAgents() {
        contextRunner
                .withUserConfiguration(RecordingSystemPromptModelConfiguration.class, DiscoveredSkillsConfiguration.class)
                .run(context -> {
                    Agent agent = context.getBean(AgentFactory.class).builder().build();
                    RecordingSystemPromptModel model = context.getBean(RecordingSystemPromptModel.class);

                    assertThat(agent.run("hello").text()).isEqualTo("ok");
                    assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).contains("activate_skill");
                    assertThat(model.systemPrompt()).contains("release-checklist");
                    assertThat(model.systemPrompt()).contains("<available_skills>");
                    assertThat(model.systemPrompt()).doesNotContain("Run mvn test before merging.");
                });
    }

    @Test
    void autoConfigurationDiscoversClasspathSkillsAndWiresThemIntoBuiltAgents() {
        contextRunner
                .withUserConfiguration(RecordingSystemPromptModelConfiguration.class)
                .run(context -> {
                    Agent agent = context.getBean(AgentFactory.class).builder().build();
                    RecordingSystemPromptModel model = context.getBean(RecordingSystemPromptModel.class);

                    assertThat(agent.run("hello").text()).isEqualTo("ok");
                    assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).contains("activate_skill");
                    assertThat(model.systemPrompt()).contains("autoconfig-release-skill");
                    assertThat(model.systemPrompt()).contains("<available_skills>");
                    assertThat(model.systemPrompt()).contains("git_status");
                    assertThat(model.systemPrompt()).doesNotContain("Run mvn test before merging and summarize the remaining risk.");
                });
    }

    @Test
    void applicationEventBridgePublishesObservationOnlyEvents() {
        contextRunner
                .withUserConfiguration(CustomModelConfiguration.class, LifecycleEventCollectorConfiguration.class)
                .run(context -> {
                    Agent agent = context.getBean(AgentFactory.class).builder().build();
                    LifecycleEventCollector collector = context.getBean(LifecycleEventCollector.class);

                    assertThat(agent.run("hello").text()).isEqualTo("ok");
                    assertThat(collector.types()).contains(
                            "beforeInvocation",
                            "messageAdded",
                            "beforeModelCall",
                            "afterModelCall",
                            "afterInvocation");
                });
    }

    @Test
    void autoConfigurationCanFilterDiscoveredToolsPerAgent() {
        contextRunner
                .withUserConfiguration(AnnotatedToolConfiguration.class, CustomModelConfiguration.class)
                .run(context -> {
                    AgentFactory factory = context.getBean(AgentFactory.class);

                    Agent plannerAgent = factory.builder().toolQualifiers("planner").build();
                    Agent supportAgent = factory.builder().toolQualifiers("support").build();

                        assertThat(plannerAgent.getTools()).extracting(tool -> tool.spec().name())
                            .contains("weather", "activate_skill")
                            .doesNotContain("supportWeather");
                        assertThat(supportAgent.getTools()).extracting(tool -> tool.spec().name())
                            .contains("supportWeather", "activate_skill")
                            .doesNotContain("weather");
                });
    }

    @Test
    void autoConfigurationBridgesSpringQualifierIntoToolQualifiers() {
        contextRunner
                .withUserConfiguration(SpringQualifiedToolConfiguration.class, CustomModelConfiguration.class)
                .run(context -> {
                    AgentFactory factory = context.getBean(AgentFactory.class);

                    Agent bridgedAgent = factory.builder().toolQualifiers("planner").build();

                        assertThat(bridgedAgent.getTools()).extracting(tool -> tool.spec().name())
                            .contains("qualifiedWeather", "activate_skill");
                });
    }

    @Test
    void autoConfigurationUsesFileSessionManagerWhenDirectoryConfigured(@TempDir java.nio.file.Path tempDir) {
        contextRunner
                .withPropertyValues("arachne.strands.agent.session.file.directory=" + tempDir)
                .run(context -> {
                    assertThat(context).hasSingleBean(SessionManager.class);
                    assertThat(context.getBean(SessionManager.class)).isInstanceOf(FileSessionManager.class);
                });
    }

    @Test
    void autoConfigurationUsesSpringSessionManagerByDefault() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(SessionManager.class);
                    assertThat(context.getBean(SessionManager.class)).isInstanceOf(SpringSessionManager.class);
                });
    }

    @Test
    void autoConfigurationUsesProvidedSessionRepository() {
        contextRunner
                .withUserConfiguration(CustomMapSessionRepositoryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SessionRepository.class);
                    assertThat(context.getBean(SessionManager.class)).isInstanceOf(SpringSessionManager.class);
                });
    }

    @Test
    void autoConfigurationRetriesModelCallsWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "arachne.strands.agent.retry.enabled=true",
                        "arachne.strands.agent.retry.max-attempts=4",
                        "arachne.strands.agent.retry.initial-delay=0ms",
                        "arachne.strands.agent.retry.max-delay=0ms")
                .withUserConfiguration(RetryEnabledModelConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ModelRetryStrategy.class);

                    Agent agent = context.getBean(AgentFactory.class).builder().build();
                    String result = agent.run("hello").text();

                    assertThat(result).isEqualTo("ok after retry");
                    assertThat(context.getBean(RetryingStubModel.class).calls()).isEqualTo(3);
                });
    }

    @Test
    void autoConfigurationLeavesRetryDisabledByDefault() {
        contextRunner
                .withUserConfiguration(AlwaysThrottledModelConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ModelRetryStrategy.class);

                    Agent agent = context.getBean(AgentFactory.class).builder().build();

                    assertThatThrownBy(() -> agent.run("hello"))
                            .isInstanceOf(ModelThrottledException.class)
                            .hasMessageContaining("throttled");
                    assertThat(context.getBean(AlwaysThrottledModel.class).calls()).isEqualTo(1);
                });
    }

    @Test
    void autoConfigurationRestoresSessionAcrossFactoryBuiltAgents() {
        contextRunner
                .withPropertyValues("arachne.strands.agent.session.id=planner")
                .withUserConfiguration(CustomModelConfiguration.class)
                .run(context -> {
                    AgentFactory factory = context.getBean(AgentFactory.class);

                    Agent first = factory.builder().build();
                    first.getState().put("city", "Osaka");
                    first.run("hello");

                    Agent restored = factory.builder().build();

                    assertThat(restored.getMessages()).hasSize(2);
                    assertThat(restored.getState().get("city")).isEqualTo("Osaka");
                });
    }

    @Test
    void autoConfigurationReusesObjectMapperForStructuredOutput() {
        contextRunner
                .withUserConfiguration(CustomObjectMapperConfiguration.class, StructuredOutputSnakeCaseModelConfiguration.class)
                .run(context -> {
                    Agent agent = context.getBean(AgentFactory.class).builder().build();

                    SnakeCaseSummary result = agent.run("hello", SnakeCaseSummary.class);

                    assertThat(result.answerText()).isEqualTo("Tokyo");
                });
    }

    @Test
    void autoConfigurationProvidesTemplateRenderer() {
        contextRunner
                .run(context -> assertThat(context).hasSingleBean(ArachneTemplateRenderer.class));
    }

    @Test
    void autoConfigurationReusesObjectMapperForTemplateRendering() {
        contextRunner
                .withUserConfiguration(CustomObjectMapperConfiguration.class)
                .run(context -> {
                    ArachneTemplateRenderer renderer = context.getBean(ArachneTemplateRenderer.class);

                    String rendered = renderer.render(
                            "classpath:/templates/snake-case-summary.txt",
                            new SnakeCaseSummary("Tokyo"));

                        assertThat(rendered).isEqualTo("Answer: Tokyo");
                });
    }

    @Test
    void autoConfigurationUsesConfiguredToolExecutionExecutor() {
        contextRunner
                .withUserConfiguration(
                        EchoToolConfiguration.class,
                        ToolUseModelConfiguration.class,
                        CustomToolExecutionExecutorConfiguration.class)
                .run(context -> {
                    Agent agent = context.getBean(AgentFactory.class).builder().build();

                    assertThat(agent.run("hello").text()).isEqualTo("done");
                    assertThat(context.getBean(RecordingExecutor.class).count()).isEqualTo(2);
                });
    }

    @Test
    void autoConfigurationAppliesExecutionContextPropagationBeans() {
        contextRunner
                .withUserConfiguration(
                        ContextPropagationToolConfiguration.class,
                        ToolUseModelConfiguration.class,
                        ExecutionContextPropagationConfiguration.class)
                .run(context -> {
                    ContextTrackingSupport support = context.getBean(ContextTrackingSupport.class);
                    support.setCurrent("request-7");
                    try {
                        Agent agent = context.getBean(AgentFactory.class).builder().build();

                        assertThat(agent.run("hello").text()).isEqualTo("done");
                        assertThat(support.observedValues()).containsExactlyInAnyOrder("request-7", "request-7");
                    } finally {
                        support.clear();
                    }
                });
    }

    @Test
    void singletonServiceCanHandleConcurrentRequestsWithFactoryOwnedRuntimes() {
        contextRunner
                .withUserConfiguration(FactoryOwnedChatServiceConfiguration.class, ConcurrentIsolationModelConfiguration.class)
                .run(context -> {
                    FactoryOwnedChatService service = context.getBean(FactoryOwnedChatService.class);
                    ConcurrentIsolationModel model = context.getBean(ConcurrentIsolationModel.class);

                    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                        Future<String> first = executor.submit(() -> service.reply("first"));
                        Future<String> second = executor.submit(() -> service.reply("second"));

                        assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                                .containsExactlyInAnyOrder("reply:first", "reply:second");
                    }

                    assertThat(model.maxConcurrentCalls()).isGreaterThanOrEqualTo(2);
                    assertThat(model.messageSizes()).containsExactlyInAnyOrder(1, 1);
                    assertThat(model.prompts()).containsExactlyInAnyOrder("first", "second");
                });
    }

    @Test
    void autoConfigurationAppliesNamedAgentModelOverrides() {
        contextRunner
                .withPropertyValues(
                        "arachne.strands.model.id=jp.amazon.nova-2-lite-v1:0",
                        "arachne.strands.model.region=ap-northeast-1",
                        "arachne.strands.agents.analyst.model.id=us.amazon.nova-pro-v1:0",
                        "arachne.strands.agents.analyst.model.region=us-east-1",
                        "arachne.strands.agents.analyst.model.bedrock.cache.system-prompt=true",
                        "arachne.strands.agents.analyst.model.bedrock.cache.tools=true")
                .withUserConfiguration(CustomModelConfiguration.class)
                .run(context -> {
                    Agent agent = context.getBean(AgentFactory.class).builder("analyst").build();

                    assertThat(agent.getModel()).isInstanceOf(BedrockModel.class);
                    assertThat(((BedrockModel) agent.getModel()).getModelId()).isEqualTo("us.amazon.nova-pro-v1:0");
                    assertThat(((BedrockModel) agent.getModel()).getRegion()).isEqualTo("us-east-1");
                    assertThat(((BedrockModel) agent.getModel()).getPromptCaching())
                            .isEqualTo(new BedrockModel.PromptCaching(true, true));
                });
    }

    @Test
    void autoConfigurationAppliesNamedAgentToolQualifiersAndSessionRestore() {
        contextRunner
                .withPropertyValues(
                        "arachne.strands.agents.planner.tool-qualifiers[0]=planner",
                        "arachne.strands.agents.planner.session.id=planner-session")
                .withUserConfiguration(AnnotatedToolConfiguration.class, CustomModelConfiguration.class)
                .run(context -> {
                    AgentFactory factory = context.getBean(AgentFactory.class);

                    Agent first = factory.builder("planner").build();
                    first.getState().put("city", "Nagoya");
                    first.run("hello");

                    Agent restored = factory.builder("planner").build();

                        assertThat(restored.getTools()).extracting(tool -> tool.spec().name())
                            .contains("weather", "activate_skill")
                            .doesNotContain("supportWeather");
                    assertThat(restored.getMessages()).hasSize(2);
                    assertThat(restored.getState().get("city")).isEqualTo("Nagoya");
                });
    }

    @Test
    void autoConfigurationLetsNamedAgentDisableGlobalRetry() {
        contextRunner
                .withPropertyValues(
                        "arachne.strands.agent.retry.enabled=true",
                        "arachne.strands.agent.retry.max-attempts=4",
                        "arachne.strands.agent.retry.initial-delay=0ms",
                        "arachne.strands.agent.retry.max-delay=0ms",
                        "arachne.strands.agents.non-retrying.retry.enabled=false")
                .withUserConfiguration(AlwaysThrottledModelConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ModelRetryStrategy.class);

                    Agent agent = context.getBean(AgentFactory.class).builder("non-retrying").build();

                    assertThatThrownBy(() -> agent.run("hello"))
                            .isInstanceOf(ModelThrottledException.class)
                            .hasMessageContaining("throttled");
                    assertThat(context.getBean(AlwaysThrottledModel.class).calls()).isEqualTo(1);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomModelConfiguration {
        @Bean
        @SuppressWarnings("unused")
        Model customModel() {
            return (messages, tools) -> List.of(
                    new ModelEvent.TextDelta("ok"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AnnotatedToolConfiguration {
        @Bean
        @SuppressWarnings("unused")
        AnnotatedToolBean annotatedToolBean() {
            return new AnnotatedToolBean();
        }
    }

    static class AnnotatedToolBean {

        @StrandsTool(qualifiers = "planner")
        public String weather() {
            return "sunny";
        }

        @StrandsTool(qualifiers = "support")
        public String supportWeather() {
            return "cloudy";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DiscoveredSkillsConfiguration {
        @Bean(name = "arachneDiscoveredSkills")
        @SuppressWarnings("unused")
        List<Skill> arachneDiscoveredSkills() {
            return List.of(new Skill(
                    "release-checklist",
                    "Use this skill when preparing a release.",
                    "Run mvn test before merging."));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AnnotatedHookConfiguration {
        @Bean
        @SuppressWarnings("unused")
        HookProvider annotatedHookProvider() {
            return new AnnotatedHookProvider();
        }
    }

    @ArachneHook
    static class AnnotatedHookProvider implements HookProvider {
        @Override
        public void registerHooks(com.mahitotsu.arachne.strands.hooks.HookRegistrar registrar) {
            registrar.beforeInvocation(event -> event.state().put("hooked", Boolean.TRUE));
            registrar.afterInvocation(event -> event.setText(event.text() + "-hooked"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LifecycleEventCollectorConfiguration {
        @Bean
        @SuppressWarnings("unused")
        LifecycleEventCollector lifecycleEventCollector() {
            return new LifecycleEventCollector();
        }
    }

    static class LifecycleEventCollector implements ApplicationListener<ArachneLifecycleApplicationEvent> {
        private final CopyOnWriteArrayList<String> types = new CopyOnWriteArrayList<>();

        @Override
        public void onApplicationEvent(@NonNull ArachneLifecycleApplicationEvent event) {
            types.add(event.type());
        }

        List<String> types() {
            return List.copyOf(types);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SpringQualifiedToolConfiguration {
        @Bean
        @SuppressWarnings("unused")
        SpringQualifiedToolBean springQualifiedToolBean() {
            return new SpringQualifiedToolBean();
        }
    }

    @Qualifier("planner")
    static class SpringQualifiedToolBean {

        @StrandsTool
        public String qualifiedWeather() {
            return "windy";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomMapSessionRepositoryConfiguration {
        @Bean
        @SuppressWarnings("unused")
        SessionRepository<MapSession> mapSessionRepository() {
            return new MapSessionRepository(new java.util.concurrent.ConcurrentHashMap<>());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RetryEnabledModelConfiguration {
        @Bean
        @SuppressWarnings("unused")
        RetryingStubModel customModel() {
            return new RetryingStubModel();
        }
    }

    static class RetryingStubModel implements Model {
        private final AtomicInteger calls = new AtomicInteger();

        int calls() {
            return calls.get();
        }

        @Override
        public Iterable<ModelEvent> converse(List<com.mahitotsu.arachne.strands.types.Message> messages, List<com.mahitotsu.arachne.strands.model.ToolSpec> tools) {
            if (calls.incrementAndGet() < 3) {
                throw new ModelThrottledException("throttled");
            }
            return List.of(
                    new ModelEvent.TextDelta("ok after retry"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RecordingSystemPromptModelConfiguration {
        @Bean
        @SuppressWarnings("unused")
        RecordingSystemPromptModel customModel() {
            return new RecordingSystemPromptModel();
        }
    }

    static class RecordingSystemPromptModel implements Model {
        private String systemPrompt;

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            throw new AssertionError("Expected the system-prompt-aware overload");
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return List.of(
                    new ModelEvent.TextDelta("ok"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        String systemPrompt() {
            return systemPrompt;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AlwaysThrottledModelConfiguration {
        @Bean
        @SuppressWarnings("unused")
        AlwaysThrottledModel customModel() {
            return new AlwaysThrottledModel();
        }
    }

    static class AlwaysThrottledModel implements Model {
        private final AtomicInteger calls = new AtomicInteger();

        int calls() {
            return calls.get();
        }

        @Override
        public Iterable<ModelEvent> converse(List<com.mahitotsu.arachne.strands.types.Message> messages, List<com.mahitotsu.arachne.strands.model.ToolSpec> tools) {
            calls.incrementAndGet();
            throw new ModelThrottledException("throttled");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomObjectMapperConfiguration {
        @Bean
        @SuppressWarnings("unused")
        ObjectMapper customObjectMapper() {
            return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StructuredOutputSnakeCaseModelConfiguration {
        @Bean
        @SuppressWarnings("unused")
        Model customModel() {
            return new StructuredOutputSnakeCaseModel();
        }
    }

    static class StructuredOutputSnakeCaseModel implements Model {
        private int calls;

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            throw new AssertionError("Structured output should use the extended overloads");
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            calls++;
            return List.of(
                    new ModelEvent.TextDelta("draft"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(10, 5)));
        }

        @Override
        public Iterable<ModelEvent> converse(
                List<Message> messages,
                List<ToolSpec> tools,
                String systemPrompt,
                ToolSelection toolSelection) {
            if (toolSelection == null) {
                return converse(messages, tools, systemPrompt);
            }
            if (calls == 1) {
                calls++;
                return List.of(
                        new ModelEvent.ToolUse(
                                "structured-1",
                                toolSelection.toolName(),
                                Map.of("answer_text", "Tokyo")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(12, 4)));
            }
            return List.of(new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(13, 2)));
        }
    }

    record SnakeCaseSummary(String answerText) {
    }

    @Configuration(proxyBeanMethods = false)
    static class EchoToolConfiguration {
        @Bean
        @SuppressWarnings("unused")
        EchoToolBean echoToolBean() {
            return new EchoToolBean();
        }
    }

    static class EchoToolBean {

        @StrandsTool(name = "echo")
        public String echo(@ToolParam String value) {
            return value;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ToolUseModelConfiguration {
        @Bean
        @SuppressWarnings("unused")
        Model customModel() {
            return new ToolUseModel();
        }
    }

    static class ToolUseModel implements Model {
        private boolean firstCall = true;

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            if (firstCall) {
                firstCall = false;
                return List.of(
                        new ModelEvent.ToolUse("tool-1", "echo", Map.of("value", "a")),
                        new ModelEvent.ToolUse("tool-2", "echo", Map.of("value", "b")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(2, 2)));
            }
            return List.of(
                    new ModelEvent.TextDelta("done"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ContextPropagationToolConfiguration {
        @Bean
        @SuppressWarnings("unused")
        ContextTrackingSupport contextTrackingSupport() {
            return new ContextTrackingSupport();
        }

        @Bean
        @SuppressWarnings("unused")
        ContextAwareEchoToolBean contextAwareEchoToolBean(ContextTrackingSupport support) {
            return new ContextAwareEchoToolBean(support);
        }
    }

    static class ContextAwareEchoToolBean {
        private final ContextTrackingSupport support;

        ContextAwareEchoToolBean(ContextTrackingSupport support) {
            this.support = support;
        }

        @StrandsTool(name = "echo")
        public String echo(@ToolParam String value) {
            support.recordCurrent();
            return value;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ExecutionContextPropagationConfiguration {
        @Bean
        @SuppressWarnings("unused")
        ExecutionContextPropagation executionContextPropagation(ContextTrackingSupport support) {
            return task -> {
                String captured = support.current();
                return () -> {
                    String previous = support.current();
                    support.setCurrent(captured);
                    try {
                        task.run();
                    } finally {
                        support.restore(previous);
                    }
                };
            };
        }
    }

    static class ContextTrackingSupport {
        private final ThreadLocal<String> current = new ThreadLocal<>();
        private final CopyOnWriteArrayList<String> observedValues = new CopyOnWriteArrayList<>();

        void setCurrent(String value) {
            current.set(value);
        }

        String current() {
            return current.get();
        }

        void restore(String value) {
            if (value == null) {
                current.remove();
            } else {
                current.set(value);
            }
        }

        void recordCurrent() {
            observedValues.add(current.get());
        }

        List<String> observedValues() {
            return List.copyOf(observedValues);
        }

        void clear() {
            current.remove();
            observedValues.clear();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomToolExecutionExecutorConfiguration {
        @Bean(name = "arachneToolExecutionExecutor")
        @SuppressWarnings("unused")
        RecordingExecutor arachneToolExecutionExecutor() {
            return new RecordingExecutor();
        }
    }

    static class RecordingExecutor implements Executor {
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public void execute(Runnable command) {
            executions.incrementAndGet();
            command.run();
        }

        int count() {
            return executions.get();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FactoryOwnedChatServiceConfiguration {
        @Bean
        @SuppressWarnings("unused")
        FactoryOwnedChatService factoryOwnedChatService(AgentFactory agentFactory) {
            return new FactoryOwnedChatService(agentFactory);
        }
    }

    static class FactoryOwnedChatService {
        private final AgentFactory agentFactory;

        FactoryOwnedChatService(AgentFactory agentFactory) {
            this.agentFactory = agentFactory;
        }

        String reply(String prompt) {
            return agentFactory.builder().build().run(prompt).text();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConcurrentIsolationModelConfiguration {
        @Bean
        @SuppressWarnings("unused")
        ConcurrentIsolationModel customModel() {
            return new ConcurrentIsolationModel();
        }
    }

    static class ConcurrentIsolationModel implements Model {
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxConcurrentCalls = new AtomicInteger();
        private final CopyOnWriteArrayList<Integer> messageSizes = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<String> prompts = new CopyOnWriteArrayList<>();

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            int concurrentCalls = inFlight.incrementAndGet();
            maxConcurrentCalls.accumulateAndGet(concurrentCalls, Math::max);
            try {
                String prompt = messages.getLast().content().stream()
                        .filter(com.mahitotsu.arachne.strands.types.ContentBlock.Text.class::isInstance)
                        .map(com.mahitotsu.arachne.strands.types.ContentBlock.Text.class::cast)
                        .map(com.mahitotsu.arachne.strands.types.ContentBlock.Text::text)
                        .findFirst()
                        .orElse("(missing prompt)");
                prompts.add(prompt);
                messageSizes.add(messages.size());
                Thread.sleep(100);
                return List.of(
                        new ModelEvent.TextDelta("reply:" + prompt),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while coordinating concurrent test", e);
            } finally {
                inFlight.decrementAndGet();
            }
        }

        int maxConcurrentCalls() {
            return maxConcurrentCalls.get();
        }

        List<Integer> messageSizes() {
            return List.copyOf(messageSizes);
        }

        List<String> prompts() {
            return List.copyOf(prompts);
        }
    }
}