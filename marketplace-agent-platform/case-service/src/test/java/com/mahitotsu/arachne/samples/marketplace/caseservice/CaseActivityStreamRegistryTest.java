package com.mahitotsu.arachne.samples.marketplace.caseservice;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class CaseActivityStreamRegistryTest {

    @Test
    void registerSendsInitialHandshakeEvent() {
        RecordingEmitter emitter = new RecordingEmitter();
        CaseActivityStreamRegistry registry = new CaseActivityStreamRegistry() {
            @Override
            SseEmitter newEmitter() {
                return emitter;
            }
        };

        registry.register("case-1");

        assertThat(registry.emitterCount("case-1")).isEqualTo(1);
        assertThat(emitter.sendCount).isEqualTo(1);
    }

    @Test
    void publishRemovesCompletedEmitterAndContinuesWithHealthySubscribers() throws Exception {
        CaseActivityStreamRegistry registry = new CaseActivityStreamRegistry();
        CompletedEmitter completedEmitter = new CompletedEmitter();
        RecordingEmitter healthyEmitter = new RecordingEmitter();

        registry.addEmitter("case-1", completedEmitter);
        registry.addEmitter("case-1", healthyEmitter);

        registry.publish("case-1", List.of(activity("EVENT_ONE"), activity("EVENT_TWO")));

        assertThat(registry.emitterCount("case-1")).isEqualTo(1);
        assertThat(healthyEmitter.sendCount).isEqualTo(2);
    }

    private static CaseContracts.ActivityEvent activity(String kind) {
        return new CaseContracts.ActivityEvent(
                kind.toLowerCase(),
                "case-1",
                OffsetDateTime.parse("2026-04-03T00:00:00Z"),
                kind,
                "workflow-service",
                "message",
                "{}");
    }

    private static final class CompletedEmitter extends SseEmitter {

        CompletedEmitter() {
            super(0L);
        }

        @Override
        public synchronized void send(@NonNull SseEventBuilder builder) throws IOException {
            throw new IllegalStateException("ResponseBodyEmitter has already completed");
        }
    }

    private static final class RecordingEmitter extends SseEmitter {

        private int sendCount;

        RecordingEmitter() {
            super(0L);
        }

        @Override
        public synchronized void send(@NonNull SseEventBuilder builder) {
            sendCount++;
        }
    }
}