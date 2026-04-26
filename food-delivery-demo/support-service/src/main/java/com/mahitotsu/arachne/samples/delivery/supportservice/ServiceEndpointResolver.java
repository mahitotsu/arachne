package com.mahitotsu.arachne.samples.delivery.supportservice;

interface ServiceEndpointResolver {

    String resolveUrl(String serviceName, String fallbackBaseUrl, String requestPath);
}