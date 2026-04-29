package com.mahitotsu.arachne.samples.delivery.menuservice.observation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.strands.skills.Skill;
import com.mahitotsu.arachne.strands.spring.ArachneLifecycleApplicationEvent;

@Component
public class ArachneLifecycleHistoryListener implements ApplicationListener<ArachneLifecycleApplicationEvent> {

    public static final String SESSION_STATE_KEY = "delivery.execution.sessionId";
    private static final String SERVICE_NAME = "menu-service";
    private static final String AGENT_NAME = "menu-agent";
    private static final String LOADED_SKILLS_STATE_KEY = "arachne.skills.loaded";

    private final MenuExecutionHistoryStore historyStore;
    private final Map<String, PendingToolCall> toolCalls = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedDeque<Long>> modelCalls = new ConcurrentHashMap<>();

    public ArachneLifecycleHistoryListener(MenuExecutionHistoryStore historyStore) {
        this.historyStore = historyStore;
    }

    @Override
    public void onApplicationEvent(@NonNull ArachneLifecycleApplicationEvent event) {
        if (event.payload() instanceof ArachneLifecycleApplicationEvent.ModelCallObservation observation) {
            onModelObservation(observation);
            return;
        }
        if (event.payload() instanceof ArachneLifecycleApplicationEvent.ToolCallObservation observation) {
            onToolObservation(observation);
        }
    }

    private void onModelObservation(ArachneLifecycleApplicationEvent.ModelCallObservation observation) {
        String sessionId = sessionId(observation.state());
        if (sessionId == null) {
            return;
        }
        if ("before".equals(observation.phase())) {
            modelCalls.computeIfAbsent(sessionId, ignored -> new ConcurrentLinkedDeque<>()).addLast(System.nanoTime());
            return;
        }
        long durationMs = elapsed(modelCalls.computeIfAbsent(sessionId, ignored -> new ConcurrentLinkedDeque<>()).pollFirst());
        historyStore.append(
                sessionId,
                "model",
                SERVICE_NAME,
                AGENT_NAME,
                "model-call",
                normalizeStopReason(observation.stopReason()),
                durationMs,
                "menu-agent model response",
                "stopReason=" + normalizeStopReason(observation.stopReason()),
                null,
                extractSkills(observation.state()));
    }

    private void onToolObservation(ArachneLifecycleApplicationEvent.ToolCallObservation observation) {
        String sessionId = sessionId(observation.state());
        if (sessionId == null) {
            return;
        }
        String key = sessionId + ":" + observation.toolUseId() + ":" + observation.toolName();
        if ("before".equals(observation.phase())) {
            toolCalls.put(key, new PendingToolCall(System.nanoTime(), summarize(observation.input())));
            return;
        }
        PendingToolCall pending = toolCalls.remove(key);
        historyStore.append(
                sessionId,
                "tool",
                SERVICE_NAME,
                observation.toolName(),
                observation.toolName(),
                normalizeToolOutcome(observation.result()),
                elapsed(pending == null ? null : pending.startedAt()),
                "menu-agent tool " + observation.toolName(),
                "input=" + (pending == null ? "" : pending.inputSummary()) + " => result=" + summarize(observation.result() == null ? null : observation.result().content()),
                null,
                extractSkills(observation.state()));
    }

    private String sessionId(Map<String, Object> state) {
        Object raw = state == null ? null : state.get(SESSION_STATE_KEY);
        if (raw instanceof String value && !value.isBlank()) {
            return value;
        }
        return null;
    }

    private List<String> extractSkills(Map<String, Object> state) {
        Object raw = state == null ? null : state.get(LOADED_SKILLS_STATE_KEY);
        if (!(raw instanceof List<?> skills)) {
            return List.of();
        }
        return skills.stream()
                .map(item -> item instanceof Skill skill ? skill.name() : String.valueOf(item))
                .toList();
    }

    private String summarize(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replaceAll("\\s+", " ").trim();
        return text.length() <= 240 ? text : text.substring(0, 237) + "...";
    }

    private long elapsed(Long startedAt) {
        if (startedAt == null) {
            return 0L;
        }
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String normalizeStopReason(String stopReason) {
        if (stopReason == null || stopReason.isBlank()) {
            return "completed";
        }
        return stopReason.toLowerCase();
    }

    private String normalizeToolOutcome(com.mahitotsu.arachne.strands.tool.ToolResult result) {
        if (result == null || result.status() == null) {
            return "unknown";
        }
        return result.status() == com.mahitotsu.arachne.strands.tool.ToolResult.ToolStatus.SUCCESS ? "success" : "error";
    }

    private record PendingToolCall(long startedAt, String inputSummary) {
    }
}