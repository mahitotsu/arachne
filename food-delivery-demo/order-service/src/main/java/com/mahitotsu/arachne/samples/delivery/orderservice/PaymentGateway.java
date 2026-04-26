package com.mahitotsu.arachne.samples.delivery.orderservice;

interface PaymentGateway {

    PaymentPrepareResponse prepare(PaymentPrepareRequest request, String accessToken);
}