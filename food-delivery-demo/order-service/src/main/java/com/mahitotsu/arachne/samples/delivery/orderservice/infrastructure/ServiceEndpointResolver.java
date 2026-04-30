package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

public interface ServiceEndpointResolver {

    String resolveUrl(String capabilityQuery, String requestPath);
}