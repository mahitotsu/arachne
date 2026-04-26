package com.mahitotsu.arachne.samples.delivery.deliveryservice;

import java.util.List;

interface EtaServiceDiscoveryGateway {

    List<EtaServiceTarget> discoverAvailableEtaServices(String query);
}