package com.mahitotsu.arachne.samples.delivery.deliveryservice.config;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "delivery")
@Validated
public class DeliveryServiceProperties {

    @Valid
    private final Registry registry = new Registry();

    @Valid
    private final Delivery delivery = new Delivery();

    @Valid
    private final Model model = new Model();

    public Registry getRegistry() {
        return registry;
    }

    public Delivery getDelivery() {
        return delivery;
    }

    public Model getModel() {
        return model;
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

    public static final class Delivery {

        @NotBlank
        private String endpoint = "http://delivery-service:8080";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = normalize(endpoint);
        }

        @AssertTrue(message = "delivery.delivery.endpoint must be an absolute http(s) URL")
        public boolean isEndpointValid() {
            return isHttpUrl(endpoint);
        }
    }

    public static final class Model {

        @NotBlank
        @Pattern(regexp = "live|deterministic", message = "delivery.model.mode must be live or deterministic")
        private String mode = "live";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = normalize(mode).toLowerCase();
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