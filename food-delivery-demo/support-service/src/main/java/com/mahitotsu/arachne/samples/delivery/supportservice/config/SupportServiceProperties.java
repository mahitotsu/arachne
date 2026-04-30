package com.mahitotsu.arachne.samples.delivery.supportservice.config;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "delivery")
@Validated
public class SupportServiceProperties {

    @Valid
    private final Registry registry = new Registry();

    @Valid
    private final Support support = new Support();

    @Valid
    private final Downstream downstream = new Downstream();

    public Registry getRegistry() {
        return registry;
    }

    public Support getSupport() {
        return support;
    }

    public Downstream getDownstream() {
        return downstream;
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

    public static final class Support {

        @NotBlank
        private String endpoint = "http://support-service:8080";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = normalize(endpoint);
        }

        @AssertTrue(message = "delivery.support.endpoint must be an absolute http(s) URL")
        public boolean isEndpointValid() {
            return isHttpUrl(endpoint);
        }
    }

    public static final class Downstream {

        @Valid
        private final Order order = new Order();

        public Order getOrder() {
            return order;
        }
    }

    public static final class Order {

        @NotBlank
        private String capabilityQuery = "注文履歴参照";

        public String getCapabilityQuery() {
            return capabilityQuery;
        }

        public void setCapabilityQuery(String capabilityQuery) {
            this.capabilityQuery = normalize(capabilityQuery);
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