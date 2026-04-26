package com.mahitotsu.arachne.samples.delivery.orderservice;

interface MenuGateway {

    MenuSuggestionResponse suggest(MenuSuggestionRequest request, String accessToken);
}