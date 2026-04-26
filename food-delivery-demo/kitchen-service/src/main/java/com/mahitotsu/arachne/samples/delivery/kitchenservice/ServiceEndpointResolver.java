package com.mahitotsu.arachne.samples.delivery.kitchenservice;

interface ServiceEndpointResolver {

    String resolveUrl(String serviceName, String fallbackBaseUrl, String requestPath);
}