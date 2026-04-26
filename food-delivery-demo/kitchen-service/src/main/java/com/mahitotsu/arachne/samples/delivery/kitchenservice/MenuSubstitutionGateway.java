package com.mahitotsu.arachne.samples.delivery.kitchenservice;

interface MenuSubstitutionGateway {

    MenuSubstitutionResponse suggestSubstitutes(MenuSubstitutionRequest request, String accessToken);
}