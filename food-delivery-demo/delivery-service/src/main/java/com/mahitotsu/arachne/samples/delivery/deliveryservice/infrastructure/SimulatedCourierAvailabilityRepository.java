package com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.*;

import java.util.List;

import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.samples.delivery.deliveryservice.config.DeliveryServiceProperties;

@Component
public class SimulatedCourierAvailabilityRepository implements CourierAvailabilityRepository {

    private final String modelMode;

    SimulatedCourierAvailabilityRepository(DeliveryServiceProperties properties) {
        this.modelMode = properties.getModel().getMode();
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