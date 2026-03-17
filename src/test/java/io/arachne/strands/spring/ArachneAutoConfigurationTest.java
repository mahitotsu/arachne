package io.arachne.strands.spring;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.bedrock.BedrockModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}