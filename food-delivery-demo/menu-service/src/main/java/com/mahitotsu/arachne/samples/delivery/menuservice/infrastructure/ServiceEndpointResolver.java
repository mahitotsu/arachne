package com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure;

public interface ServiceEndpointResolver {

    String resolveUrl(String capabilityQuery, String requestPath);
}