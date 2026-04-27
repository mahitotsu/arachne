package com.mahitotsu.arachne.samples.delivery.supportservice.domain;

public record ServiceHealthSummary(String serviceName, String status, String healthEndpoint) {
}