package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.*;

import java.util.Optional;

public interface SupportGateway {

    Optional<SupportFeedbackResponse> recordFeedback(SupportFeedbackRequestPayload request, String accessToken);
}