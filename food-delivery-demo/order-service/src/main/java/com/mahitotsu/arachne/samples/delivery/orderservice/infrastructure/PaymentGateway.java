package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.*;

public interface PaymentGateway {

    PaymentPrepareResponse prepare(PaymentPrepareRequest request, String accessToken);
}