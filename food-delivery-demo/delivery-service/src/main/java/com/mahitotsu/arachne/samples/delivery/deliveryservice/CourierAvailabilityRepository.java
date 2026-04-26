package com.mahitotsu.arachne.samples.delivery.deliveryservice;

import java.util.List;

interface CourierAvailabilityRepository {

    CourierStatus check(List<String> itemNames);
}