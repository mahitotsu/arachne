package io.arachne.strands.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import io.arachne.strands.types.Message;

class AgentSessionTest {

    @Test
    void constructorCopiesMessagesAndState() {
        List<Message> messages = new ArrayList<>(List.of(Message.user("hello")));
        Map<String, Object> state = new LinkedHashMap<>(Map.of("city", "Tokyo"));
        Map<String, Object> conversationManagerState = new LinkedHashMap<>(Map.of("summary", "done"));

        AgentSession session = new AgentSession(messages, state, conversationManagerState);
        messages.add(Message.assistant("later"));
        state.put("city", "Osaka");
        conversationManagerState.put("summary", "changed");

        assertThat(session.messages()).containsExactly(Message.user("hello"));
        assertThat(session.state()).containsEntry("city", "Tokyo");
        assertThat(session.conversationManagerState()).containsEntry("summary", "done");
    }

    @Test
    void constructorPreservesNullValuesInStateMaps() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("summary", null);
        Map<String, Object> conversationManagerState = new LinkedHashMap<>();
        conversationManagerState.put("summary", null);

        AgentSession session = new AgentSession(List.of(Message.user("hello")), state, conversationManagerState);

        assertThat(session.state()).containsEntry("summary", null);
        assertThat(session.conversationManagerState()).containsEntry("summary", null);
    }
}