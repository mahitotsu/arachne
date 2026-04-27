package com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure;

public interface ServiceEndpointResolver {

    String resolveUrl(String serviceName, String fallbackBaseUrl, String requestPath);
}