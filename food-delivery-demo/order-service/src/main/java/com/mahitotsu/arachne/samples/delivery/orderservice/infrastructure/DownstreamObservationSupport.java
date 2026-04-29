package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.DeliveryQuoteResponse;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.MenuSuggestionResponse;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.PaymentPrepareResponse;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.SupportFeedbackResponse;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Component
class DownstreamObservationSupport {

    private final ObservationRegistry observationRegistry;
    private final OrderExecutionHistoryStore historyStore;

    DownstreamObservationSupport(
            ObservationRegistry observationRegistry,
            OrderExecutionHistoryStore historyStore) {
        this.observationRegistry = observationRegistry;
        this.historyStore = historyStore;
    }

    <T> T observe(String metricName, String target, String operation, Supplier<T> action) {
        return observe(metricName, null, target, operation, operation, action);
    }

    <T> T observe(String metricName, String sessionId, String target, String operation, String requestSummary, Supplier<T> action) {
        long startedAt = System.nanoTime();
        Observation observation = Observation.start(metricName, observationRegistry)
                .lowCardinalityKeyValue(KeyValue.of("target", target))
                .lowCardinalityKeyValue(KeyValue.of("operation", operation));
        try (Observation.Scope scope = observation.openScope()) {
            T result = action.get();
            observation.lowCardinalityKeyValue(KeyValue.of("outcome", "success"));
            historyStore.append(
                    sessionId,
                    "downstream",
                    "order-service",
                    target,
                    operation,
                    "success",
                    elapsedMillis(startedAt),
                    "order-service -> " + target + " " + operation,
                    requestSummary + " => " + responseSummary(result));
            return result;
        } catch (RuntimeException ex) {
            observation.lowCardinalityKeyValue(KeyValue.of("outcome", "error"));
            observation.error(ex);
            historyStore.append(
                    sessionId,
                    "downstream",
                    "order-service",
                    target,
                    operation,
                    "error",
                    elapsedMillis(startedAt),
                    "order-service -> " + target + " " + operation,
                    requestSummary + " => error: " + ex.getMessage());
            throw ex;
        } finally {
            observation.stop();
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String responseSummary(Object result) {
        if (result instanceof MenuSuggestionResponse response) {
            return response.headline() + " / items=" + response.items().size() + " / eta=" + response.etaMinutes() + "min";
        }
        if (result instanceof DeliveryQuoteResponse response) {
            return response.headline() + " / options=" + response.options().size();
        }
        if (result instanceof PaymentPrepareResponse response) {
            return response.headline() + " / status=" + response.paymentStatus();
        }
        if (result instanceof SupportFeedbackResponse response) {
            return response.headline() + " / classification=" + response.classification();
        }
        return String.valueOf(result);
    }
}