package io.arachne.strands.spring;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.bedrock.BedrockModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}