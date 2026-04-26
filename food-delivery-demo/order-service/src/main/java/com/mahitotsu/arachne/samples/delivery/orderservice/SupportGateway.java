package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.util.Optional;

interface SupportGateway {

    Optional<SupportFeedbackResponse> recordFeedback(SupportFeedbackRequestPayload request, String accessToken);
}