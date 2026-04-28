package com.mahitotsu.arachne.samples.delivery.deliveryservice.observation;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.strands.agent.AgentResult;

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

    public AgentObservationSupport(ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    public AgentResult observe(String serviceName, String agentName, Supplier<AgentResult> action) {
        Observation observation = Observation.start(INVOCATION_METRIC, observationRegistry)
                .lowCardinalityKeyValue(KeyValue.of("service", serviceName))
                .lowCardinalityKeyValue(KeyValue.of("agent", agentName));
        try (Observation.Scope scope = observation.openScope()) {
            AgentResult result = action.get();
            observation.lowCardinalityKeyValue(KeyValue.of("outcome", "success"));
            recordUsage(serviceName, agentName, result);
            return result;
        } catch (RuntimeException ex) {
            observation.lowCardinalityKeyValue(KeyValue.of("outcome", "error"));
            observation.error(ex);
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
}