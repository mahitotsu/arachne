package com.mahitotsu.arachne.strands.session;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.mahitotsu.arachne.strands.agent.AgentState;

/**
 * In-process session storage for tests and local usage.
 */
public class InMemorySessionManager implements SessionManager {

    private final ConcurrentMap<String, AgentSession> sessions = new ConcurrentHashMap<>();

    @Override
    public AgentSession load(String sessionId) {
        AgentSession session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        return copyOf(session);
    }

    @Override
    public void save(String sessionId, AgentSession session) {
        sessions.put(sessionId, copyOf(session));
    }

    private AgentSession copyOf(AgentSession session) {
        return new AgentSession(
                List.copyOf(session.messages()),
                new AgentState(session.state()).get(),
                new LinkedHashMap<>(session.conversationManagerState()),
                List.copyOf(session.pendingInterrupts()));
    }
}