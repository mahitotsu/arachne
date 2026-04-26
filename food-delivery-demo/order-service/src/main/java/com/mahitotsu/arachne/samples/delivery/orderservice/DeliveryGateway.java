package com.mahitotsu.arachne.samples.delivery.orderservice;

interface DeliveryGateway {

    DeliveryQuoteResponse quote(DeliveryQuoteRequest request, String accessToken);
}