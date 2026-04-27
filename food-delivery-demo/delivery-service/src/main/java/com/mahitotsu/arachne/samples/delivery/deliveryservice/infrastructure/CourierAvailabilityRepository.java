package com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.*;

import java.util.List;

public interface CourierAvailabilityRepository {

    CourierStatus check(List<String> itemNames);
}