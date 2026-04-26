package com.mahitotsu.arachne.samples.delivery.deliveryservice;

import java.util.List;
import java.util.Optional;

interface ExternalEtaGateway {

    Optional<ExternalEtaQuote> quote(EtaServiceTarget service, List<String> itemNames, String context);
}