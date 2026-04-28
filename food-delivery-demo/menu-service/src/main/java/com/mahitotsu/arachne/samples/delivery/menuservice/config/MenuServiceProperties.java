package com.mahitotsu.arachne.samples.delivery.menuservice.config;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "delivery")
@Validated
public class MenuServiceProperties {

    @Valid
    private final Registry registry = new Registry();

    @Valid
    private final Menu menu = new Menu();

    @Valid
    private final Downstream downstream = new Downstream();

    public Registry getRegistry() {
        return registry;
    }

    public Menu getMenu() {
        return menu;
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

    public static final class Menu {

        @NotBlank
        private String endpoint = "http://menu-service:8080";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = normalize(endpoint);
        }

        @AssertTrue(message = "delivery.menu.endpoint must be an absolute http(s) URL")
        public boolean isEndpointValid() {
            return isHttpUrl(endpoint);
        }
    }

    public static final class Downstream {

        @Valid
        private final Kitchen kitchen = new Kitchen();

        public Kitchen getKitchen() {
            return kitchen;
        }
    }

    public static final class Kitchen {

        @NotBlank
        private String serviceName = "kitchen-service";

        private String baseUrl = "";

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

        @AssertTrue(message = "delivery.downstream.kitchen.base-url must be blank or an absolute http(s) URL")
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