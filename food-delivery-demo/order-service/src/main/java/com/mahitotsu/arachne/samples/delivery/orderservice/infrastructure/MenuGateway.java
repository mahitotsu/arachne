package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.*;

public interface MenuGateway {

    MenuSuggestionResponse suggest(MenuSuggestionRequest request, String accessToken);
}