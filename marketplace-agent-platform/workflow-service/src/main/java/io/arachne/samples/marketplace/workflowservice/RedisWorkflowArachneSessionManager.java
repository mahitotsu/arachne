package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arachne.strands.session.AgentSession;
import io.arachne.strands.session.SessionManager;
import org.springframework.data.redis.core.StringRedisTemplate;

final class RedisWorkflowArachneSessionManager implements SessionManager {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WorkflowServiceConfiguration.WorkflowSessionProperties properties;

    RedisWorkflowArachneSessionManager(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            WorkflowServiceConfiguration.WorkflowSessionProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public AgentSession load(String sessionId) {
        String payload = redisTemplate.opsForValue().get(key(sessionId));
        if (payload == null) {
            return null;
        }
        return read(payload);
    }

    @Override
    public void save(String sessionId, AgentSession session) {
        redisTemplate.opsForValue().set(key(sessionId), write(session), properties.ttl());
    }

    private String key(String sessionId) {
        return properties.keyPrefix() + "agent:" + sessionId;
    }

    private String write(AgentSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Arachne workflow session", exception);
        }
    }

    private AgentSession read(String payload) {
        try {
            return objectMapper.readValue(payload, AgentSession.class);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize Arachne workflow session", exception);
        }
    }
}