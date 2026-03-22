package io.arachne.samples.toolcontext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

@Component
public class DemoRequestContext {

    private final ThreadLocal<String> currentRequestId = new ThreadLocal<>();
    private final CopyOnWriteArrayList<String> observedRequestIds = new CopyOnWriteArrayList<>();

    public void setCurrentRequestId(String requestId) {
        currentRequestId.set(requestId);
    }

    public String currentRequestId() {
        return currentRequestId.get();
    }

    public void restore(String requestId) {
        if (requestId == null) {
            currentRequestId.remove();
        } else {
            currentRequestId.set(requestId);
        }
    }

    public void recordCurrentRequestId() {
        observedRequestIds.add(currentRequestId.get());
    }

    public List<String> observedRequestIds() {
        return List.copyOf(observedRequestIds);
    }

    public void clear() {
        currentRequestId.remove();
        observedRequestIds.clear();
    }
}
