package com.mahitotsu.arachne.samples.delivery.customerservice;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CustomerJwksController {

    private final SigningKeyManager signingKeyManager;

    CustomerJwksController(SigningKeyManager signingKeyManager) {
        this.signingKeyManager = signingKeyManager;
    }

    @GetMapping(path = "/oauth2/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> jwks() {
        return signingKeyManager.publicJwkSet().toJSONObject();
    }
}