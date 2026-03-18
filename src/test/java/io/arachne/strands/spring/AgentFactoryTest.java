package io.arachne.strands.spring;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.bedrock.BedrockModel;
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
}