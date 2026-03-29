package io.arachne.strands.session;

/**
 * Persists and restores agent sessions keyed by session id.
 */
public interface SessionManager {

    AgentSession load(String sessionId);

    void save(String sessionId, AgentSession session);
}