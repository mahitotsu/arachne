package com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.*;

public interface KitchenCheckGateway {

    KitchenCheckResponse check(KitchenCheckRequest request, String accessToken);
}