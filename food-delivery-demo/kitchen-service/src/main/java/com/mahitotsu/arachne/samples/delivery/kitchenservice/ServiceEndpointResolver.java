package com.mahitotsu.arachne.samples.delivery.kitchenservice;

interface ServiceEndpointResolver {

    String resolveUrl(String capabilityQuery, String fallbackBaseUrl, String fallbackRequestPath);
}