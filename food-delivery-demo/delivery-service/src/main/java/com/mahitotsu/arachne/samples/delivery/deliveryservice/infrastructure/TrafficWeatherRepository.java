package com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.*;

public interface TrafficWeatherRepository {

    TrafficWeatherStatus current();
}