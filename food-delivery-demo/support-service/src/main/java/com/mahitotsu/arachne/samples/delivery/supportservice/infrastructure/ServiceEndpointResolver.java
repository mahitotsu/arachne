package com.mahitotsu.arachne.samples.delivery.supportservice.infrastructure;

public interface ServiceEndpointResolver {

    String resolveUrl(String capabilityQuery, String requestPath);
}