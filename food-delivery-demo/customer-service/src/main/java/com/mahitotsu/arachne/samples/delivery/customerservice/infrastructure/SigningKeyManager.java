package com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure;

import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

public interface SigningKeyManager {

    RSAKey rsaKey();

    RSAPublicKey publicKey();

    String keyId();

    JWKSet publicJwkSet();
}