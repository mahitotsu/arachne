package io.arachne.strands.session;

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
}