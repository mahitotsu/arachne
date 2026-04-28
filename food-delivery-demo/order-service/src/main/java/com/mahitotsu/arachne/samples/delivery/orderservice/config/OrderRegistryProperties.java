package com.mahitotsu.arachne.samples.delivery.orderservice.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "delivery")
@Validated
public class OrderRegistryProperties {

    @Valid
    private final Registry registry = new Registry();

    @Valid
    private final Order order = new Order();

    @Valid
    private final Downstream downstream = new Downstream();

    public Registry getRegistry() {
        return registry;
    }

    public Order getOrder() {
        return order;
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

    public static final class Order {

        @NotBlank
        private String endpoint = "http://order-service:8080";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = normalize(endpoint);
        }

        @AssertTrue(message = "delivery.order.endpoint must be an absolute http(s) URL")
        public boolean isEndpointValid() {
            return isHttpUrl(endpoint);
        }
    }

    public static final class Downstream {

        @Valid
        private final ServiceTarget menu = new ServiceTarget("menu-service");

        @Valid
        private final ServiceTarget delivery = new ServiceTarget("delivery-service");

        @Valid
        private final ServiceTarget payment = new ServiceTarget("payment-service");

        @Valid
        private final ServiceTarget support = new ServiceTarget("support-service");

        public ServiceTarget getMenu() {
            return menu;
        }

        public ServiceTarget getDelivery() {
            return delivery;
        }

        public ServiceTarget getPayment() {
            return payment;
        }

        public ServiceTarget getSupport() {
            return support;
        }
    }

    public static final class ServiceTarget {

        @NotBlank
        private String serviceName;

        private String baseUrl = "";

        ServiceTarget(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = normalize(serviceName);
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = normalize(baseUrl);
        }

        @AssertTrue(message = "delivery.downstream service base-url must be blank or an absolute http(s) URL")
        public boolean isBaseUrlValid() {
            return baseUrl.isBlank() || isHttpUrl(baseUrl);
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