package com.mahitotsu.arachne.samples.delivery.deliveryservice;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class SimulatedCourierAvailabilityRepository implements CourierAvailabilityRepository {

    private final String modelMode;

    SimulatedCourierAvailabilityRepository(@Value("${delivery.model.mode:live}") String modelMode) {
        this.modelMode = modelMode;
    }

    @Override
    public CourierStatus check(List<String> itemNames) {
        int itemCount = itemNames == null ? 0 : itemNames.size();
        if ("deterministic".equals(modelMode)) {
            return new CourierStatus(true, 1 + itemCount, 3 + itemCount);
        }
        boolean expressAvailable = (System.currentTimeMillis() / 10000) % 3 != 0;
        int expressReadyInMinutes = expressAvailable ? (1 + itemCount) : -1;
        int standardReadyInMinutes = 3 + itemCount;
        return new CourierStatus(expressAvailable, expressReadyInMinutes, standardReadyInMinutes);
    }
}