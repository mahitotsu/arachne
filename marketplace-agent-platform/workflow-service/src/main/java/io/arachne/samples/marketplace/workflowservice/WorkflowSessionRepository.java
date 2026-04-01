package io.arachne.samples.marketplace.workflowservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.ApprovalStateView;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.EvidenceView;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.OutcomeView;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.Recommendation;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowStatus;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

interface WorkflowSessionRepository {

    Optional<WorkflowSessionState> find(String caseId);

    void save(WorkflowSessionState state);

    void delete(String caseId);
}

record WorkflowSessionState(
        String caseId,
        String caseType,
        String orderId,
        BigDecimal amount,
        String currency,
        WorkflowStatus workflowStatus,
        Recommendation currentRecommendation,
        EvidenceView evidence,
        ApprovalStateView approvalState,
        OutcomeView outcome,
        String approvalRuntimeSessionId,
        String approvalInterruptId) {
}

@Component
@ConditionalOnProperty(prefix = "workflow-session", name = "store", havingValue = "memory", matchIfMissing = true)
class InMemoryWorkflowSessionRepository implements WorkflowSessionRepository {

    private final ConcurrentHashMap<String, WorkflowSessionState> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<WorkflowSessionState> find(String caseId) {
        return Optional.ofNullable(sessions.get(caseId));
    }

    @Override
    public void save(WorkflowSessionState state) {
        sessions.put(state.caseId(), state);
    }

    @Override
    public void delete(String caseId) {
        sessions.remove(caseId);
    }
}

@Component
@ConditionalOnProperty(prefix = "workflow-session", name = "store", havingValue = "redis")
class RedisWorkflowSessionRepository implements WorkflowSessionRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WorkflowServiceConfiguration.WorkflowSessionProperties properties;

    RedisWorkflowSessionRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            WorkflowServiceConfiguration.WorkflowSessionProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<WorkflowSessionState> find(String caseId) {
        var payload = redisTemplate.opsForValue().get(key(caseId));
        if (payload == null) {
            return Optional.empty();
        }
        return Optional.of(read(payload));
    }

    @Override
    public void save(WorkflowSessionState state) {
        redisTemplate.opsForValue().set(key(state.caseId()), write(state), properties.ttl());
    }

    @Override
    public void delete(String caseId) {
        redisTemplate.delete(key(caseId));
    }

    private String key(String caseId) {
        return properties.keyPrefix() + caseId;
    }

    private String write(WorkflowSessionState state) {
        try {
            return objectMapper.writeValueAsString(state);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize workflow session state", exception);
        }
    }

    private WorkflowSessionState read(String payload) {
        try {
            return objectMapper.readValue(payload, WorkflowSessionState.class);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize workflow session state", exception);
        }
    }
}