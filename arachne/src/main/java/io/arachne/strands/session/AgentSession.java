package io.arachne.strands.session;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.arachne.strands.types.Message;

/**
 * Serializable snapshot of an agent session.
 */
public record AgentSession(
        List<Message> messages,
        Map<String, Object> state,
        Map<String, Object> conversationManagerState
) {

        public AgentSession {
                messages = List.copyOf(messages);
                state = Collections.unmodifiableMap(new LinkedHashMap<>(state));
                conversationManagerState = Collections.unmodifiableMap(new LinkedHashMap<>(conversationManagerState));
        }
}