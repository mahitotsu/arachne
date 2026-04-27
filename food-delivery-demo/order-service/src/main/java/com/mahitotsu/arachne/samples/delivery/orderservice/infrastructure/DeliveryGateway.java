package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.*;

public interface DeliveryGateway {

    DeliveryQuoteResponse quote(DeliveryQuoteRequest request, String accessToken);
}