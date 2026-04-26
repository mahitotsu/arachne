package com.mahitotsu.arachne.samples.delivery.deliveryservice;

import org.springframework.stereotype.Component;

@Component
class SimulatedTrafficWeatherRepository implements TrafficWeatherRepository {

    @Override
    public TrafficWeatherStatus current() {
        long slot = (System.currentTimeMillis() / 30000) % 4;
        int trafficDelay = (int) (slot * 3);
        int weatherDelay = slot >= 3 ? 5 : 0;
        String trafficLevel = switch ((int) slot) {
            case 0 -> "clear";
            case 1 -> "light";
            case 2 -> "moderate";
            default -> "heavy";
        };
        String weather = slot >= 3 ? "rainy" : "clear";
        return new TrafficWeatherStatus(trafficLevel, weather, trafficDelay, weatherDelay);
    }
}