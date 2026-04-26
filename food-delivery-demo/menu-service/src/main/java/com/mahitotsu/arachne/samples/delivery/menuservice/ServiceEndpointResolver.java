package com.mahitotsu.arachne.samples.delivery.menuservice;

interface ServiceEndpointResolver {

    String resolveUrl(String serviceName, String fallbackBaseUrl, String requestPath);
}