package io.arachne.samples.approvalworkflow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import io.arachne.strands.spring.ArachneLifecycleApplicationEvent;

@Component
public class LifecycleEventCollector implements ApplicationListener<ArachneLifecycleApplicationEvent> {

    private final CopyOnWriteArrayList<String> types = new CopyOnWriteArrayList<>();

    @Override
    public void onApplicationEvent(@NonNull ArachneLifecycleApplicationEvent event) {
        types.add(event.type());
    }

    public List<String> types() {
        return List.copyOf(types);
    }

    public void reset() {
        types.clear();
    }
}