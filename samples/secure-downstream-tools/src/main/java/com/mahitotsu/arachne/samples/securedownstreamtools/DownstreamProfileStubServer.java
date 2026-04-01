package com.mahitotsu.arachne.samples.securedownstreamtools;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class DownstreamProfileStubServer {

    private final ObjectMapper objectMapper;
    private final DownstreamAuditLog auditLog;
    private HttpServer server;
    private String baseUrl;

    public DownstreamProfileStubServer(ObjectMapper objectMapper, DownstreamAuditLog auditLog) {
        this.objectMapper = objectMapper;
        this.auditLog = auditLog;
    }

    @PostConstruct
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/profiles", this::handleProfile);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @PreDestroy
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public String baseUrl() {
        return baseUrl;
    }

    private void handleProfile(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String customerId = path.substring(path.lastIndexOf('/') + 1);
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        auditLog.record(customerId, authorization);

        byte[] body = objectMapper.writeValueAsBytes(Map.of(
                "customerId", customerId,
                "displayName", "Aiko Tanaka",
                "status", "ACTIVE",
                "accountFlags", new String[] {"preferred", "mfa-enrolled"}));

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        } finally {
            exchange.close();
        }
    }
}