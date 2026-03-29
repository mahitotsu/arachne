package io.arachne.samples.conversationbasics;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.agent.AgentResult;
import io.arachne.strands.spring.AgentFactory;

@Tag("integration")
@SpringBootTest
@EnabledIfSystemProperty(named = "arachne.integration.bedrock", matches = "true")
class ConversationBasicsBedrockIntegrationTest {

    @MockitoBean
    private ConversationBasicsRunner conversationBasicsRunner;

    @Autowired
    private AgentFactory agentFactory;

    @Test
    void bedrockConversationPreservesFactsAcrossTurns() {
        assertThat(conversationBasicsRunner).isNotNull();

        Agent agent = agentFactory.builder().build();

        AgentResult first = agent.run("この会話では、私の好きな色は青です。覚えてください。");
        AgentResult second = agent.run("私の好きな色は何ですか？ 一語で答えてください。");

        assertThat(first.text()).isNotBlank();
        assertThat(first.stopReason()).isEqualTo("end_turn");
        assertThat(first.metrics().usage().outputTokens()).isGreaterThan(0);

        assertThat(second.text()).isNotBlank();
        assertThat(second.text()).matches(text -> text.contains("青") || text.toLowerCase().contains("blue"),
                "expected the second turn to recall the stored favorite color");
        assertThat(second.stopReason()).isEqualTo("end_turn");
        assertThat(second.messages()).hasSize(4);
        assertThat(second.metrics().usage().inputTokens()).isGreaterThan(0);
        assertThat(agent.getMessages()).hasSize(4);
    }
}