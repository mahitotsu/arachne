package com.mahitotsu.arachne.samples.delivery.menuservice;

interface KitchenCheckGateway {

    KitchenCheckResponse check(KitchenCheckRequest request, String accessToken);
}