package com.mahitotsu.arachne.samples.delivery.registryservice;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "delivery.registry.seed-defaults=true",
                "delivery.registry.endpoint=http://registry-service:8080"
        })
class RegistryServiceApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void discoversAvailableExternalEtaProvidersAndExcludesIcarus() {
        register(new RegistryRegistration(
                "delivery-service",
                "http://delivery-service:8080",
                "配送見積もり、自社スタッフの可用性確認、交通と天候を踏まえた ETA 評価を扱う。",
                "delivery-agent",
                "配送レーンの可用性と ETA を比較して要約する。",
                List.of(new SkillPayload("delivery-routing", "配送レーン比較と ETA 評価")),
                "POST",
                "/internal/delivery/quote",
                "",
                AvailabilityStatus.AVAILABLE));
        register(new RegistryRegistration(
                "hermes-adapter",
                "http://hermes-adapter:8080",
                "外部ETAを提供する高速配送パートナー。混雑状況と料金も返す。",
                "hermes-adapter",
                "高速配送の ETA を返す。",
                List.of(new SkillPayload("partner-eta", "高速配送 ETA 見積もり")),
                "POST",
                "/adapter/eta",
                "",
                AvailabilityStatus.AVAILABLE));
        register(new RegistryRegistration(
                "idaten-adapter",
                "http://idaten-adapter:8080",
                "外部ETAを提供する低コスト配送パートナー。料金重視で ETA を返す。",
                "idaten-adapter",
                "低コスト配送の ETA を返す。",
                List.of(new SkillPayload("partner-eta", "低コスト配送 ETA 見積もり")),
                "POST",
                "/adapter/eta",
                "",
                AvailabilityStatus.AVAILABLE));

        ResponseEntity<RegistryDiscoverResponse> response = restTemplate.postForEntity(
                "/registry/discover",
                new RegistryDiscoverRequest("外部ETAを提供するサービスは？", true),
                RegistryDiscoverResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().matches())
                .extracting(RegistryServiceDescriptor::serviceName)
                .contains("hermes-adapter", "idaten-adapter")
                .doesNotContain("delivery-service")
                .doesNotContain("icarus-adapter");
        assertThat(response.getBody().summary()).contains("hermes-adapter", "idaten-adapter");
    }

    @Test
    void discoversMenuServiceForSubstitutionQueriesAndListsIcarusInServices() {
        register(new RegistryRegistration(
                "menu-service",
                "http://menu-service:8080",
                "メニュー提案、欠品時の代替候補提示、カテゴリ検索、合計金額計算を扱う。",
                "menu-agent",
                "メニュー候補と代替案を返す。",
                List.of(new SkillPayload("menu-substitution", "メニュー代替候補と提案理由を整理する")),
                "POST",
                "/internal/menu/suggest",
                "",
                AvailabilityStatus.AVAILABLE));

        ResponseEntity<RegistryDiscoverResponse> discoverResponse = restTemplate.postForEntity(
                "/registry/discover",
                new RegistryDiscoverRequest("メニュー代替を扱うサービスは？", true),
                RegistryDiscoverResponse.class);

        assertThat(discoverResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(discoverResponse.getBody()).isNotNull();
        assertThat(discoverResponse.getBody().matches())
                .extracting(RegistryServiceDescriptor::serviceName)
                .contains("menu-service");

        ResponseEntity<RegistryServiceDescriptor[]> servicesResponse = restTemplate.getForEntity(
                "/registry/services",
                RegistryServiceDescriptor[].class);

        assertThat(servicesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(servicesResponse.getBody()).isNotNull();
        assertThat(List.of(servicesResponse.getBody()))
                .extracting(RegistryServiceDescriptor::serviceName, RegistryServiceDescriptor::status)
                .contains(org.assertj.core.groups.Tuple.tuple("icarus-adapter", AvailabilityStatus.NOT_AVAILABLE));
    }

        @Test
        void marksCustomAvailableHealthEndpointsAsAvailable() throws Exception {
                try (HealthStubServer stubServer = new HealthStubServer("{\"status\":\"AVAILABLE\",\"service\":\"idaten-adapter\"}")) {
                        register(new RegistryRegistration(
                                        "idaten-adapter",
                                        "http://idaten-adapter:8080",
                                        "外部ETAを提供する低コスト配送パートナー。常時利用可能で料金優先の配送候補を返す。",
                                        "idaten-adapter",
                                        "低コスト配送の ETA と料金を返す Idaten アダプター。",
                                        List.of(new SkillPayload("partner-eta", "低コスト配送の ETA と料金見積もり")),
                                        "POST",
                                        "/adapter/eta",
                                        stubServer.healthEndpoint(),
                                        AvailabilityStatus.AVAILABLE));

                        ResponseEntity<RegistryHealthResponse> response = restTemplate.getForEntity(
                                        "/registry/health",
                                        RegistryHealthResponse.class);

                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).isNotNull();
                        assertThat(response.getBody().services())
                                        .extracting(RegistryHealthEntry::serviceName, RegistryHealthEntry::status)
                                        .contains(org.assertj.core.groups.Tuple.tuple("idaten-adapter", AvailabilityStatus.AVAILABLE));
                }
        }

    private void register(RegistryRegistration request) {
        ResponseEntity<RegistryServiceDescriptor> response = restTemplate.postForEntity(
                "/registry/register",
                request,
                RegistryServiceDescriptor.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

        private static final class HealthStubServer implements AutoCloseable {

                private final HttpServer server;

                private HealthStubServer(String responseBody) throws IOException {
                        this.server = HttpServer.create(new InetSocketAddress(0), 0);
                        this.server.createContext("/health", new JsonHandler(responseBody));
                        this.server.start();
                }

                private String healthEndpoint() {
                        return "http://127.0.0.1:" + server.getAddress().getPort() + "/health";
                }

                @Override
                public void close() {
                        server.stop(0);
                }
        }

        private static final class JsonHandler implements HttpHandler {

                private final byte[] body;

                private JsonHandler(String body) {
                        this.body = body.getBytes();
                }

                @Override
                public void handle(HttpExchange exchange) throws IOException {
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream outputStream = exchange.getResponseBody()) {
                                outputStream.write(body);
                        }
                }
        }
}