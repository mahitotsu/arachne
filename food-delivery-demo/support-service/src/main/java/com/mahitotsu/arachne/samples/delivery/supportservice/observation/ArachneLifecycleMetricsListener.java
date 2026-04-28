package com.mahitotsu.arachne.samples.delivery.supportservice.observation;

import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.strands.spring.ArachneLifecycleApplicationEvent;
import com.mahitotsu.arachne.strands.tool.ToolResult;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class ArachneLifecycleMetricsListener implements ApplicationListener<ArachneLifecycleApplicationEvent> {

    private static final String SERVICE_NAME = "support-service";
    private static final String AGENT_NAME = "support-agent";

    private final MeterRegistry meterRegistry;

    public ArachneLifecycleMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onApplicationEvent(@NonNull ArachneLifecycleApplicationEvent event) {
        if (event.payload() instanceof ArachneLifecycleApplicationEvent.ModelCallObservation observation
                && "after".equals(observation.phase())) {
            Counter.builder("delivery.agent.model.call")
                    .tag("service", SERVICE_NAME)
                    .tag("agent", AGENT_NAME)
                    .tag("outcome", normalizeStopReason(observation.stopReason()))
                    .register(meterRegistry)
                    .increment();
            return;
        }
        if (event.payload() instanceof ArachneLifecycleApplicationEvent.ToolCallObservation observation
                && "after".equals(observation.phase())) {
            Counter.builder("delivery.agent.tool.call")
                    .tag("service", SERVICE_NAME)
                    .tag("agent", AGENT_NAME)
                    .tag("tool", observation.toolName())
                    .tag("outcome", normalizeToolOutcome(observation.result()))
                    .register(meterRegistry)
                    .increment();
        }
    }

    private String normalizeStopReason(String stopReason) {
        if (stopReason == null || stopReason.isBlank()) {
            return "completed";
        }
        return stopReason.toLowerCase();
    }

    private String normalizeToolOutcome(ToolResult result) {
        if (result == null || result.status() == null) {
            return "unknown";
        }
        return result.status() == ToolResult.ToolStatus.SUCCESS ? "success" : "error";
    }
}