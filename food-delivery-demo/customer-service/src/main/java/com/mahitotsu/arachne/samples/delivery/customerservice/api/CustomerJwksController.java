package com.mahitotsu.arachne.samples.delivery.customerservice.api;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure.SigningKeyManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Customer Security", description = "JWKS exposure used by downstream resource servers in the demo environment.")
public class CustomerJwksController {

    private final SigningKeyManager signingKeyManager;

    CustomerJwksController(SigningKeyManager signingKeyManager) {
        this.signingKeyManager = signingKeyManager;
    }

    @GetMapping(path = "/oauth2/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Expose the JWKS", description = "Returns the public JWK set used by downstream services to validate customer-service JWTs.")
    Map<String, Object> jwks() {
        return signingKeyManager.publicJwkSet().toJSONObject();
    }
}