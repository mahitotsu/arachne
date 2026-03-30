package io.arachne.samples.marketplace.caseservice;

import io.arachne.samples.marketplace.caseservice.CaseContracts.ActivityEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
class CaseActivityStreamRegistry {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByCaseId = new ConcurrentHashMap<>();

    SseEmitter register(String caseId) {
        var emitter = new SseEmitter(0L);
        emittersByCaseId.computeIfAbsent(caseId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(caseId, emitter));
        emitter.onTimeout(() -> remove(caseId, emitter));
        emitter.onError(ignored -> remove(caseId, emitter));
        return emitter;
    }

    void publish(String caseId, List<ActivityEvent> events) {
        var emitters = emittersByCaseId.get(caseId);
        if (emitters == null) {
            return;
        }
        for (var emitter : emitters) {
            for (var event : events) {
                try {
                    emitter.send(SseEmitter.event().name("activity").data(event));
                }
                catch (IOException exception) {
                    remove(caseId, emitter);
                }
            }
        }
    }

    private void remove(String caseId, SseEmitter emitter) {
        var emitters = emittersByCaseId.get(caseId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByCaseId.remove(caseId);
        }
    }
}