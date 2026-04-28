package com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Component
class DownstreamObservationSupport {

    private final ObservationRegistry observationRegistry;

    DownstreamObservationSupport(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    <T> T observe(String metricName, String target, String operation, Supplier<T> action) {
        Observation observation = Observation.start(metricName, observationRegistry)
                .lowCardinalityKeyValue(KeyValue.of("target", target))
                .lowCardinalityKeyValue(KeyValue.of("operation", operation));
        try (Observation.Scope scope = observation.openScope()) {
            T result = action.get();
            observation.lowCardinalityKeyValue(KeyValue.of("outcome", "success"));
            return result;
        } catch (RuntimeException ex) {
            observation.lowCardinalityKeyValue(KeyValue.of("outcome", "error"));
            observation.error(ex);
            throw ex;
        } finally {
            observation.stop();
        }
    }
}