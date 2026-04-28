package com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.*;

import java.util.List;
import java.util.Optional;

public interface ExternalEtaGateway {

    Optional<ExternalEtaQuote> quote(EtaServiceTarget service, List<String> itemNames, DeliveryPreferenceInput preference);
}