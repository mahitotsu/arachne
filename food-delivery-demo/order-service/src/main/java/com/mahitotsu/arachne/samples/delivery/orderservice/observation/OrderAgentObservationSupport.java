package com.mahitotsu.arachne.samples.delivery.orderservice.observation;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure.OrderExecutionHistoryStore;
import com.mahitotsu.arachne.strands.agent.AgentResult;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Component
public class OrderAgentObservationSupport {

    private static final String INVOCATION_METRIC = "delivery.agent.invocation";
    private static final String TOKEN_USAGE_METRIC = "delivery.agent.usage.tokens";
    private static final String CACHE_USAGE_METRIC = "delivery.agent.usage.cache";

    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;
    private final OrderExecutionHistoryStore historyStore;

    public OrderAgentObservationSupport(
            ObservationRegistry observationRegistry,
            MeterRegistry meterRegistry,
            OrderExecutionHistoryStore historyStore) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
        this.historyStore = historyStore;
    }

    public AgentResult observe(
            String serviceName,
            String agentName,
            String sessionId,
            String prompt,
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
                summarize(prompt));
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
                    summarize(result == null ? null : result.text()));
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
                    summarize(ex.getMessage()));
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

    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 237) + "...";
    }
}