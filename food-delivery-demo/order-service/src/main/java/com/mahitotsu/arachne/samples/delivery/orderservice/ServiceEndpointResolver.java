package com.mahitotsu.arachne.samples.delivery.orderservice;

interface ServiceEndpointResolver {

    String resolveUrl(String capabilityQuery, String fallbackBaseUrl, String fallbackRequestPath);
}