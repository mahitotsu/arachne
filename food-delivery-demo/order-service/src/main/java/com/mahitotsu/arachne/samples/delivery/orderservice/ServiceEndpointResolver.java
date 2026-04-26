package com.mahitotsu.arachne.samples.delivery.orderservice;

interface ServiceEndpointResolver {

    String resolveUrl(String serviceName, String fallbackBaseUrl, String requestPath);
}