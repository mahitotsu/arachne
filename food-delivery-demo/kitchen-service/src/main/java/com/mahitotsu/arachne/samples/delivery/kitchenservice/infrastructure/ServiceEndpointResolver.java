package com.mahitotsu.arachne.samples.delivery.kitchenservice.infrastructure;

public interface ServiceEndpointResolver {

    String resolveUrl(String capabilityQuery, String requestPath);
}