package com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

@Component
public class InMemorySigningKeyManager implements SigningKeyManager {

    private final RSAKey rsaKey;
    private final RSAPublicKey publicKey;

    InMemorySigningKeyManager() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            this.publicKey = (RSAPublicKey) pair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
            this.rsaKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID("customer-key-" + UUID.randomUUID().toString().substring(0, 8))
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate the customer-service RSA key pair", ex);
        }
    }

    @Override
    public RSAKey rsaKey() {
        return rsaKey;
    }

    @Override
    public RSAPublicKey publicKey() {
        return publicKey;
    }

    @Override
    public String keyId() {
        return rsaKey.getKeyID();
    }

    @Override
    public JWKSet publicJwkSet() {
        return new JWKSet(rsaKey.toPublicJWK());
    }
}