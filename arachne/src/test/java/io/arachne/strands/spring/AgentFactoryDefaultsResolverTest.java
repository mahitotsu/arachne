package io.arachne.strands.spring;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.bedrock.BedrockModel;
import io.arachne.strands.model.retry.ExponentialBackoffRetryStrategy;

class AgentFactoryDefaultsResolverTest {

    @Test
    void resolveDefaultBuilderDefaultsCopiesSharedSettings() {
        ArachneProperties properties = new ArachneProperties();
        properties.getModel().setId("jp.amazon.nova-2-lite-v1:0");
        properties.getModel().getBedrock().getCache().setSystemPrompt(true);
        properties.getAgent().setSystemPrompt("base prompt");
        properties.getAgent().getBuiltIns().setToolNames(List.of("current_time"));
        properties.getAgent().getBuiltIns().setToolGroups(List.of("resource"));
        properties.getAgent().getSession().setId("shared-session");
        ExponentialBackoffRetryStrategy retryStrategy = new ExponentialBackoffRetryStrategy(3, Duration.ZERO, Duration.ZERO);
        Model defaultModel = (messages, tools) -> List.of(new ModelEvent.Metadata("end_turn", ModelEvent.ZERO_USAGE));

        AgentFactoryDefaultsResolver.BuilderDefaults defaults = new AgentFactoryDefaultsResolver(
                properties,
                defaultModel,
                retryStrategy)
                .resolve(null);

        assertThat(defaults.defaultModel()).isSameAs(defaultModel);
        assertThat(defaults.systemPrompt()).isEqualTo("base prompt");
        assertThat(defaults.builtInToolNames()).containsExactly("current_time");
        assertThat(defaults.builtInToolGroups()).containsExactly("resource");
        assertThat(defaults.sessionId()).isEqualTo("shared-session");
        assertThat(defaults.retryStrategy()).isSameAs(retryStrategy);
        assertThat(defaults.modelProperties().getBedrock().getCache().isSystemPrompt()).isTrue();
    }

    @Test
    void resolveNamedBuilderDefaultsMergesNamedOverridesWithoutChangingEntryPoint() {
        ArachneProperties properties = new ArachneProperties();
        properties.getModel().setId("jp.amazon.nova-2-lite-v1:0");
        properties.getModel().setRegion("ap-northeast-1");
        properties.getAgent().setSystemPrompt("shared prompt");
        properties.getAgent().getBuiltIns().setToolNames(List.of("current_time"));
        properties.getAgent().getBuiltIns().setToolGroups(List.of("utility"));
        properties.getAgent().getSession().setId("shared-session");
        properties.getAgent().getRetry().setEnabled(true);
        properties.getAgent().getRetry().setMaxAttempts(5);
        properties.getAgent().getRetry().setInitialDelay(Duration.ofMillis(10));
        properties.getAgent().getRetry().setMaxDelay(Duration.ofMillis(20));

        ArachneProperties.NamedAgentProperties analyst = new ArachneProperties.NamedAgentProperties();
        analyst.setSystemPrompt("named prompt");
        analyst.setUseDiscoveredTools(false);
        analyst.getBuiltIns().setInheritDefaults(false);
        analyst.getBuiltIns().setToolGroups(List.of("resource"));
        analyst.getSession().setId("analyst-session");
        analyst.getModel().getBedrock().getCache().setTools(true);
        analyst.getRetry().setMaxAttempts(2);
        analyst.getRetry().setInitialDelay(Duration.ZERO);
        analyst.getRetry().setMaxDelay(Duration.ZERO);
        properties.getAgents().put("analyst", analyst);

        Model sharedModel = (messages, tools) -> List.of(new ModelEvent.Metadata("end_turn", ModelEvent.ZERO_USAGE));

        AgentFactoryDefaultsResolver.BuilderDefaults defaults = new AgentFactoryDefaultsResolver(
                properties,
                sharedModel,
                null)
                .resolve("analyst");

        assertThat(defaults.systemPrompt()).isEqualTo("named prompt");
        assertThat(defaults.useDiscoveredTools()).isFalse();
        assertThat(defaults.inheritBuiltInTools()).isFalse();
        assertThat(defaults.builtInToolNames()).containsExactly("current_time");
        assertThat(defaults.builtInToolGroups()).containsExactlyInAnyOrder("utility", "resource");
        assertThat(defaults.sessionId()).isEqualTo("analyst-session");
        assertThat(defaults.defaultModel()).isInstanceOf(BedrockModel.class);
        BedrockModel model = (BedrockModel) defaults.defaultModel();
        assertThat(model.getModelId()).isEqualTo("jp.amazon.nova-2-lite-v1:0");
        assertThat(model.getRegion()).isEqualTo("ap-northeast-1");
        assertThat(model.getPromptCaching().tools()).isTrue();
        assertThat(defaults.retryStrategy()).isInstanceOf(ExponentialBackoffRetryStrategy.class);
    }

    @Test
    void resolveRejectsUnknownNamedAgent() {
        AgentFactoryDefaultsResolver resolver = new AgentFactoryDefaultsResolver(new ArachneProperties(), null, null);

        assertThatThrownBy(() -> resolver.resolve("missing"))
                .isInstanceOf(NamedAgentNotFoundException.class)
                .hasMessageContaining("missing");
    }
}