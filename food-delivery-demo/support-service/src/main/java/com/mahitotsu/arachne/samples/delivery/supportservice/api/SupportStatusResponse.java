package com.mahitotsu.arachne.samples.delivery.supportservice.api;

import java.util.List;

import com.mahitotsu.arachne.samples.delivery.supportservice.domain.ServiceHealthSummary;

public record SupportStatusResponse(String service, String agent, String summary, List<ServiceHealthSummary> services) {
}