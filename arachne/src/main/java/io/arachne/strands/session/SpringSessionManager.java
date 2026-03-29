package io.arachne.strands.session;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.arachne.strands.agent.AgentState;
import io.arachne.strands.types.Message;

/**
 * Session manager backed by Spring Session's {@link SessionRepository}.
 *
 * <p>Arachne keeps using explicit agent session ids while delegating persistence to
 * Spring Session repositories such as {@link org.springframework.session.MapSessionRepository},
 * {@link RedisSessionRepository}, and {@link RedisIndexedSessionRepository}.
 */
public class SpringSessionManager implements SessionManager {

    static final String MESSAGES_ATTRIBUTE = "arachne.messages";
    static final String STATE_ATTRIBUTE = "arachne.state";
    static final String CONVERSATION_MANAGER_STATE_ATTRIBUTE = "arachne.conversationManagerState";
    private static final TypeReference<List<Message>> MESSAGE_LIST_TYPE = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final String REDIS_SESSION_CLASS_NAME =
            "org.springframework.session.data.redis.RedisSessionRepository$RedisSession";
        private static final String REDIS_INDEXED_SESSION_CLASS_NAME =
            "org.springframework.session.data.redis.RedisIndexedSessionRepository$RedisSession";
        private static final String JDBC_REPOSITORY_CLASS_NAME =
            "org.springframework.session.jdbc.JdbcIndexedSessionRepository";
        private static final String JDBC_SESSION_CLASS_NAME =
            "org.springframework.session.jdbc.JdbcIndexedSessionRepository$JdbcSession";

    private final SessionRepository<? extends Session> sessionRepository;
    private final ObjectMapper objectMapper;

    public SpringSessionManager(SessionRepository<? extends Session> sessionRepository) {
        this(sessionRepository, new ObjectMapper());
    }

    public SpringSessionManager(SessionRepository<? extends Session> sessionRepository, ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentSession load(String sessionId) {
        Session session = sessionRepository.findById(sessionId);
        if (session == null) {
            return null;
        }

        List<Message> messages = readMessages(session);
        Map<String, Object> state = readState(session, STATE_ATTRIBUTE);
        Map<String, Object> conversationManagerState = readState(session, CONVERSATION_MANAGER_STATE_ATTRIBUTE);

        return new AgentSession(messages, state, conversationManagerState);
    }

    @Override
    public void save(String sessionId, AgentSession agentSession) {
        Session session = sessionRepository.findById(sessionId);
        if (session == null) {
            session = createSession(sessionId);
        }

        session.setAttribute(MESSAGES_ATTRIBUTE, writeJson(List.copyOf(agentSession.messages())));
        session.setAttribute(STATE_ATTRIBUTE, writeJson(new AgentState(agentSession.state()).get()));
        session.setAttribute(
                CONVERSATION_MANAGER_STATE_ATTRIBUTE,
                writeJson(new LinkedHashMap<>(agentSession.conversationManagerState())));

        saveSession(session);
    }

    @SuppressWarnings("unchecked")
    private void saveSession(Session session) {
        ((SessionRepository<Session>) sessionRepository).save(session);
    }

    private Session createSession(String sessionId) {
        if (sessionRepository instanceof RedisIndexedSessionRepository redisIndexedSessionRepository) {
            return instantiateRedisSession(
                    redisIndexedSessionRepository,
                    RedisIndexedSessionRepository.class,
                    REDIS_INDEXED_SESSION_CLASS_NAME,
                    sessionId);
        }
        if (sessionRepository instanceof RedisSessionRepository redisSessionRepository) {
            return instantiateRedisSession(
                    redisSessionRepository,
                    RedisSessionRepository.class,
                    REDIS_SESSION_CLASS_NAME,
                    sessionId);
        }
        if (isRepositoryType(sessionRepository, JDBC_REPOSITORY_CLASS_NAME)) {
            return instantiateJdbcSession(sessionRepository, sessionId);
        }

        Session session = sessionRepository.createSession();
        if (session == null) {
            throw new IllegalStateException("sessionRepository.createSession() must not return null");
        }
        if (session instanceof MapSession mapSession) {
            mapSession.setId(sessionId);
            return mapSession;
        }

        String createdSessionId = session.getId();
        if (createdSessionId == null) {
            throw new IllegalStateException("session id must not be null");
        }
        if (sessionId.equals(createdSessionId)) {
            return session;
        }

        throw new IllegalStateException(
                "Unsupported Spring Session repository for explicit agent session ids: "
                        + sessionRepository.getClass().getName());
    }

    private Session instantiateRedisSession(
            Object repository,
            Class<?> repositoryType,
            String sessionClassName,
            String sessionId) {
        try {
            Class<?> sessionClass = Class.forName(sessionClassName);
            Constructor<?> constructor = sessionClass.getDeclaredConstructor(repositoryType, MapSession.class, boolean.class);
            constructor.setAccessible(true);
            return (Session) constructor.newInstance(repository, new MapSession(sessionId), true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to create a Spring Session-backed session for id: " + sessionId,
                    e);
        }
    }

    private Session instantiateJdbcSession(Object repository, String sessionId) {
        try {
            Class<?> repositoryClass = Class.forName(JDBC_REPOSITORY_CLASS_NAME);
            Class<?> sessionClass = Class.forName(JDBC_SESSION_CLASS_NAME);
            Constructor<?> constructor = sessionClass.getDeclaredConstructor(
                    repositoryClass,
                    MapSession.class,
                    String.class,
                    boolean.class);
            constructor.setAccessible(true);
            return (Session) constructor.newInstance(repository, new MapSession(sessionId), UUID.randomUUID().toString(), true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to create a Spring Session-backed JDBC session for id: " + sessionId,
                    e);
        }
    }

    private boolean isRepositoryType(Object repository, String repositoryClassName) {
        try {
            return Class.forName(repositoryClassName).isInstance(repository);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private List<Message> readMessages(Session session) {
        Object messages = session.getAttribute(MESSAGES_ATTRIBUTE);
        if (messages instanceof String json) {
            return readJson(json, MESSAGE_LIST_TYPE, MESSAGES_ATTRIBUTE);
        }
        if (messages instanceof List<?> messageList) {
            return messageList.stream()
                    .filter(Message.class::isInstance)
                    .map(Message.class::cast)
                    .toList();
        }
        return List.of();
    }

    private Map<String, Object> readState(Session session, String attributeName) {
        Object value = session.getAttribute(attributeName);
        if (value instanceof String json) {
            return readJson(json, MAP_TYPE, attributeName);
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return copy;
        }
        return Map.of();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Failed to serialize Spring Session payload", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> typeReference, String attributeName) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "Failed to deserialize Spring Session attribute: " + attributeName,
                    e);
        }
    }
}