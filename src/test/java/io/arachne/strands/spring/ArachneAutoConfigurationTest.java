package io.arachne.strands.spring;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.ModelThrottledException;
import io.arachne.strands.model.bedrock.BedrockModel;
import io.arachne.strands.model.retry.ModelRetryStrategy;
import io.arachne.strands.session.FileSessionManager;
import io.arachne.strands.session.SessionManager;
import io.arachne.strands.session.SpringSessionManager;
import io.arachne.strands.tool.annotation.StrandsTool;

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
}