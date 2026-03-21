package io.arachne.strands.spring;

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

import io.arachne.strands.agent.Agent;
import io.arachne.strands.hooks.HookProvider;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.ModelThrottledException;
import io.arachne.strands.model.ToolSelection;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.model.bedrock.BedrockModel;
import io.arachne.strands.model.retry.ModelRetryStrategy;
import io.arachne.strands.session.FileSessionManager;
import io.arachne.strands.session.SessionManager;
import io.arachne.strands.session.SpringSessionManager;
import io.arachne.strands.skills.Skill;
import io.arachne.strands.tool.annotation.StrandsTool;
import io.arachne.strands.tool.annotation.ToolParam;
import io.arachne.strands.types.Message;

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

                    assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).contains("weather");
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
                    assertThat(model.systemPrompt()).contains("release-checklist");
                    assertThat(model.systemPrompt()).contains("Run mvn test before merging.");
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

                    assertThat(plannerAgent.getTools()).extracting(tool -> tool.spec().name()).containsExactly("weather");
                    assertThat(supportAgent.getTools()).extracting(tool -> tool.spec().name()).containsExactly("supportWeather");
                });
    }

    @Test
    void autoConfigurationBridgesSpringQualifierIntoToolQualifiers() {
        contextRunner
                .withUserConfiguration(SpringQualifiedToolConfiguration.class, CustomModelConfiguration.class)
                .run(context -> {
                    AgentFactory factory = context.getBean(AgentFactory.class);

                    Agent bridgedAgent = factory.builder().toolQualifiers("planner").build();

                    assertThat(bridgedAgent.getTools()).extracting(tool -> tool.spec().name()).containsExactly("qualifiedWeather");
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
                        "arachne.strands.agents.analyst.model.region=us-east-1")
                .withUserConfiguration(CustomModelConfiguration.class)
                .run(context -> {
                    Agent agent = context.getBean(AgentFactory.class).builder("analyst").build();

                    assertThat(agent.getModel()).isInstanceOf(BedrockModel.class);
                    assertThat(((BedrockModel) agent.getModel()).getModelId()).isEqualTo("us.amazon.nova-pro-v1:0");
                    assertThat(((BedrockModel) agent.getModel()).getRegion()).isEqualTo("us-east-1");
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

                    assertThat(restored.getTools()).extracting(tool -> tool.spec().name()).containsExactly("weather");
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
        public void registerHooks(io.arachne.strands.hooks.HookRegistrar registrar) {
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
        public Iterable<ModelEvent> converse(List<io.arachne.strands.types.Message> messages, List<io.arachne.strands.model.ToolSpec> tools) {
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
        public Iterable<ModelEvent> converse(List<io.arachne.strands.types.Message> messages, List<io.arachne.strands.model.ToolSpec> tools) {
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
                        .filter(io.arachne.strands.types.ContentBlock.Text.class::isInstance)
                        .map(io.arachne.strands.types.ContentBlock.Text.class::cast)
                        .map(io.arachne.strands.types.ContentBlock.Text::text)
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