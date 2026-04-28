package com.mahitotsu.arachne.samples.delivery.customerservice.config;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "delivery")
@Validated
public class CustomerServiceProperties {

    @Valid
    private final Registry registry = new Registry();

    @Valid
    private final Customer customer = new Customer();

    @Valid
    private final Auth auth = new Auth();

    public Registry getRegistry() {
        return registry;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Auth getAuth() {
        return auth;
    }

    public static final class Registry {

        private String baseUrl = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = normalize(baseUrl);
        }

        @AssertTrue(message = "delivery.registry.base-url must be blank or an absolute http(s) URL")
        public boolean isBaseUrlValid() {
            return baseUrl.isBlank() || isHttpUrl(baseUrl);
        }
    }

    public static final class Customer {

        @NotBlank
        private String endpoint = "http://customer-service:8080";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = normalize(endpoint);
        }

        @AssertTrue(message = "delivery.customer.endpoint must be an absolute http(s) URL")
        public boolean isEndpointValid() {
            return isHttpUrl(endpoint);
        }
    }

    public static final class Auth {

        @NotBlank
        private String issuer = "http://customer-service:8080";

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = normalize(issuer);
        }

        @AssertTrue(message = "delivery.auth.issuer must be an absolute http(s) URL")
        public boolean isIssuerValid() {
            return isHttpUrl(issuer);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}