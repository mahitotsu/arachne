package com.mahitotsu.arachne.samples.delivery.registryservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.registryservice.domain.RegistryTypes.*;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RegistryRepository {

    private static final Map<String, List<String>> CANONICAL_TERMS = Map.ofEntries(
            Map.entry("eta", List.of("eta", "配送", "配達", "delivery", "courier", "partner", "外部", "shipping")),
            Map.entry("external", List.of("external", "outside", "外部", "partner", "adapter", "アダプター", "アダプタ")),
            Map.entry("menu", List.of("menu", "メニュー", "catalog", "item", "料理")),
            Map.entry("substitute", List.of("substitute", "substitution", "fallback", "代替", "欠品")),
            Map.entry("kitchen", List.of("kitchen", "調理", "inventory", "stock", "在庫")),
            Map.entry("payment", List.of("payment", "支払い", "決済")),
            Map.entry("customer", List.of("customer", "顧客", "auth", "認証", "profile")),
            Map.entry("support", List.of("support", "faq", "feedback", "campaign", "サポート")));

    private final Map<String, RegistryRegistration> entries = new ConcurrentHashMap<>();
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    RegistryRepository(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public void register(RegistryRegistration request) {
        entries.put(request.serviceName(), normalize(request));
    }

    public RegistryServiceDescriptor describe(String serviceName) {
        RegistryRegistration entry = entries.get(serviceName);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown service: " + serviceName);
        }
        return toDescriptor(entry);
    }

    public List<RegistryServiceDescriptor> discover(String query, boolean availableOnly) {
        String safeQuery = Objects.requireNonNullElse(query, "");
        LinkedHashSet<String> queryTerms = canonicalTerms(safeQuery);
        return entries.values().stream()
                .map(this::toDescriptor)
                .filter(descriptor -> !availableOnly || descriptor.status() == AvailabilityStatus.AVAILABLE)
                .filter(descriptor -> matchesQueryIntent(queryTerms, descriptor))
                .map(descriptor -> new RankedDescriptor(descriptor, score(safeQuery, queryTerms, descriptor)))
                .filter(rank -> rank.score() > 0)
                .sorted(Comparator.comparingInt(RankedDescriptor::score).reversed()
                        .thenComparing(rank -> rank.descriptor().serviceName()))
                .map(RankedDescriptor::descriptor)
                .toList();
    }

    public List<RegistryServiceDescriptor> services() {
        return entries.values().stream()
                .map(this::toDescriptor)
                .sorted(Comparator.comparing(RegistryServiceDescriptor::serviceName))
                .toList();
    }

    public List<RegistryHealthEntry> health() {
        return entries.values().stream()
                .map(entry -> new RegistryHealthEntry(entry.serviceName(), resolveAvailability(entry), entry.healthEndpoint()))
                .sorted(Comparator.comparing(RegistryHealthEntry::serviceName))
                .toList();
    }

    private RegistryRegistration normalize(RegistryRegistration request) {
        return new RegistryRegistration(
                request.serviceName(),
                Objects.requireNonNullElse(request.endpoint(), ""),
                Objects.requireNonNullElse(request.capability(), ""),
                Objects.requireNonNullElse(request.agentName(), request.serviceName()),
                Objects.requireNonNullElse(request.systemPrompt(), ""),
                request.skills() == null ? List.of() : List.copyOf(request.skills()),
                request.tools() == null ? List.of() : List.copyOf(request.tools()),
                blankToDefault(request.requestMethod(), "POST"),
                Objects.requireNonNullElse(request.requestPath(), ""),
                Objects.requireNonNullElse(request.healthEndpoint(), ""),
                request.status() == null ? AvailabilityStatus.AVAILABLE : request.status());
    }

    private RegistryServiceDescriptor toDescriptor(RegistryRegistration entry) {
        return new RegistryServiceDescriptor(
                entry.serviceName(),
                entry.endpoint(),
                entry.capability(),
                entry.agentName(),
                entry.systemPrompt(),
                entry.skills(),
                entry.tools(),
                entry.requestMethod(),
                entry.requestPath(),
                resolveAvailability(entry));
    }

    private AvailabilityStatus resolveAvailability(RegistryRegistration entry) {
        if (entry.status() == AvailabilityStatus.NOT_AVAILABLE) {
            return AvailabilityStatus.NOT_AVAILABLE;
        }
        if (entry.healthEndpoint().isBlank()) {
            return entry.status();
        }
        try {
            String body = restClient.get()
                    .uri(entry.healthEndpoint())
                    .retrieve()
                    .body(String.class);
            String status = extractHealthStatus(body);
            if (status == null) {
                return AvailabilityStatus.AVAILABLE;
            }
            status = status.toUpperCase(Locale.ROOT);
            if ("UP".equals(status) || "AVAILABLE".equals(status)) {
                return AvailabilityStatus.AVAILABLE;
            }
            if ("DOWN".equals(status) || "NOT_AVAILABLE".equals(status)) {
                return AvailabilityStatus.NOT_AVAILABLE;
            }
            return entry.status();
        } catch (Exception ignored) {
            return AvailabilityStatus.NOT_AVAILABLE;
        }
    }

    private String extractHealthStatus(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode statusNode = root.get("status");
        if (statusNode == null || statusNode.isNull()) {
            return null;
        }
        String status = statusNode.asText();
        return status == null || status.isBlank() ? null : status;
    }

    private int score(String query, LinkedHashSet<String> queryTerms, RegistryServiceDescriptor descriptor) {
        LinkedHashSet<String> targetTerms = descriptorTerms(descriptor);
        int score = 0;
        for (String term : queryTerms) {
            if (targetTerms.contains(term)) {
                score += 10;
            }
        }
        String normalizedTarget = (descriptor.serviceName() + ' ' + descriptor.capability() + ' ' + descriptor.agentName())
                .toLowerCase(Locale.ROOT);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        if (queryTerms.contains("external") && isExternalCandidate(descriptor, targetTerms)) {
            score += 40;
        }
        if (queryTerms.contains("eta") && supportsEta(descriptor, targetTerms)) {
            score += 20;
        }
        if (queryTerms.contains("menu") && targetTerms.contains("menu")) {
            score += 15;
        }
        if (queryTerms.contains("substitute") && targetTerms.contains("substitute")) {
            score += 15;
        }
        if (normalizedQuery.contains("eta") && normalizedTarget.contains("eta")) {
            score += 5;
        }
        if (normalizedQuery.contains("代替") && normalizedTarget.contains("代替")) {
            score += 5;
        }
        if (normalizedQuery.contains("メニュー") && normalizedTarget.contains("menu")) {
            score += 5;
        }
        return score;
    }

    private boolean matchesQueryIntent(LinkedHashSet<String> queryTerms, RegistryServiceDescriptor descriptor) {
        LinkedHashSet<String> targetTerms = descriptorTerms(descriptor);
        if (queryTerms.contains("external") && !isExternalCandidate(descriptor, targetTerms)) {
            return false;
        }
        if (queryTerms.contains("eta") && !supportsEta(descriptor, targetTerms)) {
            return false;
        }
        if (queryTerms.contains("menu") && !targetTerms.contains("menu")) {
            return false;
        }
        if (queryTerms.contains("substitute") && !(targetTerms.contains("substitute") || targetTerms.contains("menu"))) {
            return false;
        }
        return true;
    }

    private LinkedHashSet<String> descriptorTerms(RegistryServiceDescriptor descriptor) {
        return canonicalTerms(
                descriptor.serviceName() + ' ' + descriptor.capability() + ' ' + descriptor.agentName() + ' '
                        + descriptor.systemPrompt() + ' ' + descriptor.requestPath() + ' '
                        + descriptor.skills().stream().map(skill -> skill.name() + ' ' + skill.content()).collect(Collectors.joining(" ")));
    }

    private boolean isExternalCandidate(RegistryServiceDescriptor descriptor, LinkedHashSet<String> targetTerms) {
        return targetTerms.contains("external")
                || descriptor.serviceName().contains("adapter")
                || descriptor.requestPath().contains("/adapter/");
    }

    private boolean supportsEta(RegistryServiceDescriptor descriptor, LinkedHashSet<String> targetTerms) {
        return targetTerms.contains("eta")
                || descriptor.requestPath().toLowerCase(Locale.ROOT).contains("eta");
    }

    private LinkedHashSet<String> canonicalTerms(String text) {
        String lowered = Objects.requireNonNullElse(text, "").toLowerCase(Locale.ROOT);
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : CANONICAL_TERMS.entrySet()) {
            boolean matched = entry.getValue().stream().anyMatch(lowered::contains);
            if (matched) {
                terms.add(entry.getKey());
            }
        }
        for (String token : lowered.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+")) {
            if (!token.isBlank()) {
                terms.add(token);
            }
        }
        return terms;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}