package com.mahitotsu.arachne.samples.delivery.registryservice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.ToolResult;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

@SpringBootApplication
public class RegistryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegistryServiceApplication.class, args);
    }

    @Bean
    ApplicationRunner seedDefaults(
            RegistryRepository repository,
            @Value("${delivery.registry.seed-defaults:true}") boolean seedDefaults,
            @Value("${delivery.registry.endpoint:http://registry-service:8080}") String registryEndpoint) {
        return args -> {
            if (!seedDefaults) {
                return;
            }
            repository.register(new RegistryRegistration(
                    "registry-service",
                    registryEndpoint,
                    "サービスのケイパビリティ発見と稼働状況集約。自然言語クエリから適切な内部サービスやアダプターを案内する。",
                    "capability-registry-agent",
                    "登録済みサービスのケイパビリティと稼働状況を照合し、利用可能な候補を返す。",
                    List.of(new SkillPayload("capability-match", "ケイパビリティ記述と問い合わせ文を照合して候補を返す")),
                    "POST",
                    "/registry/discover",
                    registryEndpoint + "/actuator/health",
                    AvailabilityStatus.AVAILABLE));
            repository.register(new RegistryRegistration(
                    "icarus-adapter",
                    "",
                    "外部ETAを提供するプレミアム配送パートナー。現時点では停止中のため候補からは除外する。",
                    "icarus-adapter",
                    "プレミアム配送 ETA を返す想定だが、現在は停止中。",
                    List.of(new SkillPayload("premium-eta", "プレミアム配送の ETA 見積もり")),
                    "POST",
                    "/adapter/eta",
                    "",
                    AvailabilityStatus.NOT_AVAILABLE));
        };
    }

    @Bean
    Model capabilityRegistryDeterministicModel() {
        return new CapabilityRegistryDeterministicModel();
    }
}

@RestController
@RequestMapping(path = "/registry", produces = MediaType.APPLICATION_JSON_VALUE)
class RegistryController {

    private final RegistryApplicationService applicationService;

    RegistryController(RegistryApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/register")
    RegistryServiceDescriptor register(@RequestBody RegistryRegistration request) {
        return applicationService.register(request);
    }

    @PostMapping("/discover")
    RegistryDiscoverResponse discover(@RequestBody RegistryDiscoverRequest request) {
        return applicationService.discover(request);
    }

    @GetMapping("/services")
    List<RegistryServiceDescriptor> services() {
        return applicationService.services();
    }

    @GetMapping("/health")
    RegistryHealthResponse health() {
        return applicationService.health();
    }
}

@Service
class RegistryApplicationService {

    private static final String DISCOVERY_PROMPT = """
            あなたは capability-registry-agent です。
            capability_match ツールを使って、問い合わせ文に合う利用可能なサービス候補を探してください。
            回答は見つかったサービス名を簡潔に要約してください。
            """;

    private final RegistryRepository repository;
    private final AgentFactory agentFactory;
    private final Tool capabilityMatchTool;

    RegistryApplicationService(RegistryRepository repository, AgentFactory agentFactory, Tool capabilityMatchTool) {
        this.repository = repository;
        this.agentFactory = agentFactory;
        this.capabilityMatchTool = capabilityMatchTool;
    }

    RegistryServiceDescriptor register(RegistryRegistration request) {
        repository.register(request);
        return repository.describe(request.serviceName());
    }

    RegistryDiscoverResponse discover(RegistryDiscoverRequest request) {
        boolean availableOnly = request.availableOnly() == null || request.availableOnly();
        List<RegistryServiceDescriptor> matches = repository.discover(request.query(), availableOnly);
        String summary = agentFactory.builder()
                .systemPrompt(DISCOVERY_PROMPT)
                .tools(capabilityMatchTool)
                .build()
                .run("問い合わせ: " + request.query())
                .text();
        return new RegistryDiscoverResponse("registry-service", "capability-registry-agent", summary, matches);
    }

    List<RegistryServiceDescriptor> services() {
        return repository.services();
    }

    RegistryHealthResponse health() {
        return new RegistryHealthResponse(repository.health());
    }
}

@Component
class RegistryRepository {

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

    void register(RegistryRegistration request) {
        entries.put(request.serviceName(), normalize(request));
    }

    RegistryServiceDescriptor describe(String serviceName) {
        RegistryRegistration entry = entries.get(serviceName);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown service: " + serviceName);
        }
        return toDescriptor(entry);
    }

    List<RegistryServiceDescriptor> discover(String query, boolean availableOnly) {
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

    List<RegistryServiceDescriptor> services() {
        return entries.values().stream()
                .map(this::toDescriptor)
                .sorted(Comparator.comparing(RegistryServiceDescriptor::serviceName))
                .toList();
    }

    List<RegistryHealthEntry> health() {
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

@Configuration
class RegistryArachneConfiguration {

    @Bean
    Tool capabilityMatchTool(RegistryRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode properties = root.putObject("properties");
                properties.putObject("query")
                        .put("type", "string")
                        .put("description", "自然言語のサービス発見クエリ");
                properties.putObject("availableOnly")
                        .put("type", "boolean")
                        .put("description", "AVAILABLE の候補だけに絞るかどうか");
                root.putArray("required").add("query");
                root.put("additionalProperties", false);
                return new ToolSpec("capability_match",
                        "登録済みサービスのケイパビリティ説明を自然言語クエリと照合し、利用可能な候補を返す。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("capability_match", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                String query = String.valueOf(values.getOrDefault("query", ""));
                boolean availableOnly = Boolean.parseBoolean(String.valueOf(values.getOrDefault("availableOnly", true)));
                List<Map<String, Object>> matches = repository.discover(query, availableOnly).stream()
                        .<Map<String, Object>>map(match -> {
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            mapped.put("serviceName", match.serviceName());
                            mapped.put("endpoint", match.endpoint());
                            mapped.put("capability", match.capability());
                            mapped.put("agentName", match.agentName());
                            mapped.put("requestMethod", match.requestMethod());
                            mapped.put("requestPath", match.requestPath());
                            mapped.put("status", match.status().name());
                            return mapped;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("matches", matches));
            }
        };
    }

    private static Map<String, Object> values(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
    }
}

final class CapabilityRegistryDeterministicModel implements Model {

    @Override
    public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
        return converse(messages, tools, null, null);
    }

    @Override
    public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
        return converse(messages, tools, systemPrompt, null);
    }

    @Override
    public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt, ToolSelection toolSelection) {
        Map<String, Object> capabilityResult = latestToolContent(messages, "capability-match");
        if (capabilityResult == null) {
            return List.of(
                    new ModelEvent.ToolUse("capability-match", "capability_match", Map.of(
                            "query", latestUserText(messages),
                            "availableOnly", true)),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }
        List<String> names = extractServiceNames(capabilityResult);
        String summary = names.isEmpty()
                ? "利用可能な候補は見つかりませんでした。"
                : "候補: " + String.join("、", names);
        return List.of(
                new ModelEvent.TextDelta(summary),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
    }

    private Map<String, Object> latestToolContent(List<Message> messages, String toolUseId) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.ToolResult result
                        && toolUseId.equals(result.toolUseId())
                        && result.content() instanceof Map<?, ?> content) {
                    LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                    content.forEach((key, value) -> values.put(String.valueOf(key), value));
                    return values;
                }
            }
        }
        return null;
    }

    private String latestUserText(List<Message> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            List<String> texts = message.content().stream()
                    .filter(ContentBlock.Text.class::isInstance)
                    .map(ContentBlock.Text.class::cast)
                    .map(ContentBlock.Text::text)
                    .toList();
            if (!texts.isEmpty()) {
                return String.join(" ", texts);
            }
        }
        return "";
    }

    private List<String> extractServiceNames(Map<String, Object> capabilityResult) {
        Object rawMatches = capabilityResult.get("matches");
        if (!(rawMatches instanceof List<?> matches)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Object match : matches) {
            if (match instanceof Map<?, ?> matchMap) {
                Object serviceName = matchMap.get("serviceName");
                if (serviceName != null) {
                    names.add(String.valueOf(serviceName));
                }
            }
        }
        return names;
    }
}

record RegistryRegistration(
        String serviceName,
        String endpoint,
        String capability,
        String agentName,
        String systemPrompt,
        List<SkillPayload> skills,
        String requestMethod,
        String requestPath,
        String healthEndpoint,
        AvailabilityStatus status) {}

record SkillPayload(String name, String content) {}

record RegistryDiscoverRequest(String query, Boolean availableOnly) {}

record RegistryDiscoverResponse(String service, String agent, String summary, List<RegistryServiceDescriptor> matches) {}

record RegistryServiceDescriptor(
        String serviceName,
        String endpoint,
        String capability,
        String agentName,
        String systemPrompt,
        List<SkillPayload> skills,
        String requestMethod,
        String requestPath,
        AvailabilityStatus status) {}

record RegistryHealthResponse(List<RegistryHealthEntry> services) {}

record RegistryHealthEntry(String serviceName, AvailabilityStatus status, String healthEndpoint) {}

record RankedDescriptor(RegistryServiceDescriptor descriptor, int score) {}

enum AvailabilityStatus {
    AVAILABLE,
    NOT_AVAILABLE
}