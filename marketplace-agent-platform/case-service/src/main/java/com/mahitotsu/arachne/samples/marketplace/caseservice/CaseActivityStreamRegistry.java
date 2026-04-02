package com.mahitotsu.arachne.samples.marketplace.caseservice;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.ActivityEvent;

@Component
class CaseActivityStreamRegistry {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByCaseId = new ConcurrentHashMap<>();

    SseEmitter register(String caseId) {
        var emitter = new SseEmitter(0L);
        addEmitter(caseId, emitter);
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
                    emitter.send(SseEmitter.event().name("activity").data((Object) event));
                }
                catch (IOException | IllegalStateException exception) {
                    discard(caseId, emitter);
                    break;
                }
            }
        }
    }

    void addEmitter(String caseId, SseEmitter emitter) {
        emittersByCaseId.computeIfAbsent(caseId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    int emitterCount(String caseId) {
        var emitters = emittersByCaseId.get(caseId);
        return emitters == null ? 0 : emitters.size();
    }

    private void discard(String caseId, SseEmitter emitter) {
        remove(caseId, emitter);
        try {
            emitter.complete();
        }
        catch (IllegalStateException ignored) {
            // The emitter already completed on the caller or container side.
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