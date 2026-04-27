package com.mahitotsu.arachne.samples.delivery.kitchenservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.kitchenservice.domain.KitchenTypes.*;

public interface MenuSubstitutionGateway {

    MenuSubstitutionResponse suggestSubstitutes(MenuSubstitutionRequest request, String accessToken);
}