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
        private final ServiceTarget menu = new ServiceTarget("メニュー提案 在庫付き提案 注文候補");

        @Valid
        private final ServiceTarget delivery = new ServiceTarget("配送候補 ETA 比較 配送選択肢");

        @Valid
        private final ServiceTarget payment = new ServiceTarget("支払い準備 合計確認 課金確定");

        @Valid
        private final ServiceTarget support = new ServiceTarget("注文後フィードバック受付 問い合わせ受付 サポート連携");

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
        private String capabilityQuery;

        ServiceTarget(String capabilityQuery) {
            this.capabilityQuery = capabilityQuery;
        }

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