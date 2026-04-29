package com.mahitotsu.arachne.samples.delivery.supportservice.observation;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.samples.delivery.supportservice.domain.SupportExecutionHistoryTypes.AgentUsageBreakdown;
import com.mahitotsu.arachne.strands.agent.AgentResult;
import com.mahitotsu.arachne.strands.agent.AgentState;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Component
public class AgentObservationSupport {

    private static final String INVOCATION_METRIC = "delivery.agent.invocation";
    private static final String TOKEN_USAGE_METRIC = "delivery.agent.usage.tokens";
    private static final String CACHE_USAGE_METRIC = "delivery.agent.usage.cache";

    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;
    private final SupportExecutionHistoryStore historyStore;

    public AgentObservationSupport(
            ObservationRegistry observationRegistry,
            MeterRegistry meterRegistry,
            SupportExecutionHistoryStore historyStore) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
        this.historyStore = historyStore;
    }

    public AgentResult observe(String serviceName, String agentName, Supplier<AgentResult> action) {
        return observe(serviceName, agentName, null, "", new AgentState(), action);
    }

    public AgentResult observe(
            String serviceName,
            String agentName,
            String sessionId,
            String prompt,
            AgentState state,
            Supplier<AgentResult> action) {
        long startedAt = System.nanoTime();
        historyStore.append(
                sessionId,
                "agent",
                serviceName,
                agentName,
                "invoke",
                "started",
                0L,
                agentName + " invocation started",
                summarize(prompt),
                null,
                List.of());
        Observation observation = Observation.start(INVOCATION_METRIC, observationRegistry)
                .lowCardinalityKeyValue(KeyValue.of("service", serviceName))
                .lowCardinalityKeyValue(KeyValue.of("agent", agentName));
        try (Observation.Scope scope = observation.openScope()) {
            AgentResult result = action.get();
            observation.lowCardinalityKeyValue(KeyValue.of("outcome", "success"));
            recordUsage(serviceName, agentName, result);
            historyStore.append(
                    sessionId,
                    "agent",
                    serviceName,
                    agentName,
                    "invoke",
                    "success",
                    elapsedMillis(startedAt),
                    agentName + " invocation completed",
                    summarize(result.text()),
                    usage(result),
                    skillNames(state));
            return result;
        } catch (RuntimeException ex) {
            observation.lowCardinalityKeyValue(KeyValue.of("outcome", "error"));
            observation.error(ex);
            historyStore.append(
                    sessionId,
                    "agent",
                    serviceName,
                    agentName,
                    "invoke",
                    "error",
                    elapsedMillis(startedAt),
                    agentName + " invocation failed",
                    summarize(ex.getMessage()),
                    null,
                    skillNames(state));
            throw ex;
        } finally {
            observation.stop();
        }
    }

    private void recordUsage(String serviceName, String agentName, AgentResult result) {
        if (result == null || result.metrics() == null || result.metrics().usage() == null) {
            return;
        }
        var usage = result.metrics().usage();
        increment(TOKEN_USAGE_METRIC, serviceName, agentName, "input", usage.inputTokens());
        increment(TOKEN_USAGE_METRIC, serviceName, agentName, "output", usage.outputTokens());
        increment(CACHE_USAGE_METRIC, serviceName, agentName, "read", usage.cacheReadInputTokens());
        increment(CACHE_USAGE_METRIC, serviceName, agentName, "write", usage.cacheWriteInputTokens());
    }

    private void increment(String metricName, String serviceName, String agentName, String type, int amount) {
        if (amount <= 0) {
            return;
        }
        Counter.builder(metricName)
                .tag("service", serviceName)
                .tag("agent", agentName)
                .tag("type", type)
                .register(meterRegistry)
                .increment(amount);
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private AgentUsageBreakdown usage(AgentResult result) {
        if (result == null || result.metrics() == null || result.metrics().usage() == null) {
            return null;
        }
        var usage = result.metrics().usage();
        return new AgentUsageBreakdown(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cacheReadInputTokens(),
                usage.cacheWriteInputTokens());
    }

    private List<String> skillNames(AgentState state) {
        Object raw = state == null ? null : state.get("arachne.skills.loaded");
        if (!(raw instanceof List<?> skills)) {
            return List.of();
        }
        return skills.stream()
                .map(item -> item instanceof com.mahitotsu.arachne.strands.skills.Skill skill ? skill.name() : String.valueOf(item))
                .toList();
    }

    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 237) + "...";
    }
}