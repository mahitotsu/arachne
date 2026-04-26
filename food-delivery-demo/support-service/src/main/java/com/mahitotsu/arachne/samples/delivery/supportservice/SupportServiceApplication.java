package com.mahitotsu.arachne.samples.delivery.supportservice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

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
public class SupportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportServiceApplication.class, args);
    }

    @Bean
    SecurityFilterChain supportSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                .build();
    }

    @Bean
    ApplicationRunner registerSupportService(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl,
            @Value("${DELIVERY_SUPPORT_ENDPOINT:http://support-service:8080}") String serviceEndpoint) {
        return args -> {
            if (registryBaseUrl.isBlank()) {
                return;
            }
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "serviceName", "support-service",
                            "endpoint", serviceEndpoint,
                            "capability", "FAQ回答、キャンペーン案内、問い合わせ受付、サービス稼働状況共有を扱う。",
                            "agentName", "support-agent",
                            "systemPrompt", "FAQ、キャンペーン、問い合わせ、稼働状況を整理し、必要なら注文履歴も参照して案内する。",
                            "skills", List.of(Map.of("name", "support-guide", "content", "FAQ、問い合わせ、キャンペーン、稼働状況のサポート導線")),
                            "requestMethod", "POST",
                            "requestPath", "/api/support/chat",
                            "healthEndpoint", serviceEndpoint + "/actuator/health",
                            "status", "AVAILABLE"))
                    .retrieve()
                    .toBodilessEntity();
        };
    }
}

@RestController
@RequestMapping(path = "/api/support", produces = MediaType.APPLICATION_JSON_VALUE)
class SupportController {

    private final SupportApplicationService applicationService;

    SupportController(SupportApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    SupportChatResponse chat(@RequestBody SupportChatRequest request) {
        return applicationService.chat(request);
    }

    @PostMapping(path = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    SupportFeedbackResponse feedback(@RequestBody SupportFeedbackRequest request) {
        return applicationService.feedback(request);
    }

    @GetMapping("/campaigns")
    List<CampaignSummary> campaigns() {
        return applicationService.campaigns();
    }

    @GetMapping("/status")
    SupportStatusResponse status() {
        return applicationService.status();
    }
}

@Service
class SupportApplicationService {

    private static final String SUPPORT_PROMPT = """
            あなたは support-agent です。
            問い合わせ内容に応じて faq_lookup、campaign_lookup、service_status_lookup、feedback_lookup、order_history_lookup を使い分けてください。
            FAQ と現在有効なキャンペーン、稼働状況を優先して整理し、注文内容の変更や再注文が必要なら [HANDOFF: order] を含めて案内してください。
            回答は日本語で簡潔にまとめてください。
            """;

    private final AgentFactory agentFactory;
    private final FaqRepository faqRepository;
    private final CampaignRepository campaignRepository;
    private final FeedbackRepository feedbackRepository;
    private final SupportStatusGateway statusGateway;
    private final OrderHistoryGateway orderHistoryGateway;
    private final OrderHistorySnapshotStore orderHistorySnapshotStore;
    private final Tool faqLookupTool;
    private final Tool campaignLookupTool;
    private final Tool serviceStatusLookupTool;
    private final Tool feedbackLookupTool;
    private final Tool orderHistoryLookupTool;

    SupportApplicationService(
            AgentFactory agentFactory,
            FaqRepository faqRepository,
            CampaignRepository campaignRepository,
            FeedbackRepository feedbackRepository,
            SupportStatusGateway statusGateway,
            OrderHistoryGateway orderHistoryGateway,
            OrderHistorySnapshotStore orderHistorySnapshotStore,
            Tool faqLookupTool,
            Tool campaignLookupTool,
            Tool serviceStatusLookupTool,
            Tool feedbackLookupTool,
            Tool orderHistoryLookupTool) {
        this.agentFactory = agentFactory;
        this.faqRepository = faqRepository;
        this.campaignRepository = campaignRepository;
        this.feedbackRepository = feedbackRepository;
        this.statusGateway = statusGateway;
        this.orderHistoryGateway = orderHistoryGateway;
        this.orderHistorySnapshotStore = orderHistorySnapshotStore;
        this.faqLookupTool = faqLookupTool;
        this.campaignLookupTool = campaignLookupTool;
        this.serviceStatusLookupTool = serviceStatusLookupTool;
        this.feedbackLookupTool = feedbackLookupTool;
        this.orderHistoryLookupTool = orderHistoryLookupTool;
    }

    SupportChatResponse chat(SupportChatRequest request) {
        SupportIntent intent = SupportIntent.fromMessage(request.message());
        String safeMessage = Objects.requireNonNullElse(request.message(), "");
        String accessToken = SecurityAccessors.requiredAccessToken();
        String customerId = SecurityAccessors.currentCustomerId();
        List<FaqEntry> faqMatches = intent.includesFaq() ? faqRepository.lookup(safeMessage, 3) : List.of();
        List<CampaignSummary> campaigns = intent.includesCampaigns() ? campaignRepository.activeCampaigns() : List.of();
        List<ServiceHealthSummary> statuses = intent.includesStatuses() ? statusGateway.currentStatuses() : List.of();
        List<CustomerOrderHistoryEntry> recentOrders = intent.includesOrderHistory()
                ? orderHistoryGateway.recentOrders(accessToken)
                : List.of();
        if (intent.includesOrderHistory()) {
            orderHistorySnapshotStore.cache(customerId, recentOrders);
        }
        List<FeedbackInsight> relatedFeedback = intent.includesFeedback() ? feedbackRepository.lookup(safeMessage, 3) : List.of();
        HandoffInstruction handoff = HandoffInstruction.fromMessage(safeMessage);

        String summary = agentFactory.builder()
                .systemPrompt(SUPPORT_PROMPT)
                .tools(faqLookupTool, campaignLookupTool, serviceStatusLookupTool, feedbackLookupTool, orderHistoryLookupTool)
                .build()
                .run("問い合わせ: " + safeMessage + "。customerId=" + customerId)
                .text();

        return new SupportChatResponse(
                request.sessionId(),
                "support-service",
                "support-agent",
                headline(intent),
                summary,
                faqMatches,
                campaigns,
                statuses,
                recentOrders,
                relatedFeedback,
                handoff.target(),
                handoff.message());
    }

    SupportFeedbackResponse feedback(SupportFeedbackRequest request) {
        SupportFeedbackRecord feedback = feedbackRepository.record(SecurityAccessors.currentCustomerId(), request);
        String headline = feedback.escalationRequired()
                ? "support-agent が問い合わせを要確認として記録しました"
                : "support-agent が問い合わせを受け付けました";
        String summary = feedback.escalationRequired()
                ? feedback.classification() + " 問い合わせとして記録し、担当確認が必要なためエスカレーション対象にしました。"
                : feedback.classification() + " 問い合わせとして記録しました。";
        return new SupportFeedbackResponse(
                "support-service",
                "support-agent",
                headline,
                summary,
                feedback.classification(),
                feedback.escalationRequired());
    }

    List<CampaignSummary> campaigns() {
        return campaignRepository.activeCampaigns();
    }

    SupportStatusResponse status() {
        List<ServiceHealthSummary> services = statusGateway.currentStatuses();
        String summary = services.isEmpty()
                ? "support-service は現在の稼働状況を取得できませんでした。"
                : "稼働状況: " + services.stream()
                        .map(service -> service.serviceName() + "=" + service.status())
                        .limit(5)
                        .reduce((left, right) -> left + "、" + right)
                        .orElse("取得済み");
        return new SupportStatusResponse("support-service", "support-agent", summary, services);
    }

    private String headline(SupportIntent intent) {
        List<String> topics = new ArrayList<>();
        if (intent.includesCampaigns()) {
            topics.add("キャンペーン");
        }
        if (intent.includesStatuses()) {
            topics.add("稼働状況");
        }
        if (intent.includesOrderHistory()) {
            topics.add("注文履歴");
        }
        if (intent.includesFeedback()) {
            topics.add("問い合わせ事例");
        }
        if (intent.includesFaq()) {
            topics.add("FAQ");
        }
        if (topics.isEmpty()) {
            return "support-agent がお問い合わせを受け付けました";
        }
        return "support-agent が " + String.join("・", topics) + " を案内しました";
    }
}

@Component
class FaqRepository {

    private final List<FaqEntry> entries = List.of(
            new FaqEntry(
                    "faq-cancel",
                    "注文をキャンセルできますか？",
                    "調理前であれば注文チャットから変更やキャンセルを案内できます。必要なら support-agent が order-service への handoff を案内します。",
                    List.of("キャンセル", "注文変更", "refund")),
            new FaqEntry(
                    "faq-payment",
                    "使える支払い方法を教えてください。",
                    "現在はデモ用にカード決済を想定しています。支払い準備は payment-service が注文確定前に再計算します。",
                    List.of("支払い", "payment", "カード")),
            new FaqEntry(
                    "faq-delivery",
                    "配送が遅いときはどうなりますか？",
                    "配送状況は support-service から稼働状況を確認できます。再注文や配送変更が必要なら注文チャットへ引き継ぎます。",
                    List.of("配送", "遅延", "eta", "問い合わせ")));

    List<FaqEntry> lookup(String query, int limit) {
        LinkedHashSet<String> terms = terms(query);
        return entries.stream()
                .map(entry -> new RankedFaq(entry, score(entry, terms)))
                .filter(rank -> rank.score() > 0)
                .sorted(Comparator.comparingInt(RankedFaq::score).reversed()
                        .thenComparing(rank -> rank.entry().id()))
                .map(RankedFaq::entry)
                .limit(limit)
                .toList();
    }

    private int score(FaqEntry entry, LinkedHashSet<String> terms) {
        String haystack = (entry.question() + " " + entry.answer() + " " + String.join(" ", entry.tags()))
                .toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (haystack.contains(term)) {
                score += 10;
            }
        }
        return score;
    }

    private LinkedHashSet<String> terms(String text) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String lowered = Objects.requireNonNullElse(text, "").toLowerCase(Locale.ROOT);
        for (String token : lowered.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+")) {
            if (!token.isBlank()) {
                terms.add(token);
            }
        }
        if (terms.isEmpty()) {
            terms.add("faq");
        }
        return terms;
    }

    private record RankedFaq(FaqEntry entry, int score) {
    }
}

@Component
class CampaignRepository {

    private final List<CampaignSummary> campaigns = List.of(
            new CampaignSummary(
                    "cmp-rainy-day",
                    "雨の日ポイント2倍",
                    "雨天時の注文でポイントを通常の2倍付与します。",
                    "期間限定",
                    "2026-05-31"),
            new CampaignSummary(
                    "cmp-family-lunch",
                    "ファミリーランチセット割",
                    "セット商品を2つ以上選ぶと合計金額から300円引きになります。",
                    "人気",
                    "2026-06-15"));

    List<CampaignSummary> activeCampaigns() {
        return campaigns;
    }
}

@Component
class FeedbackRepository {

    private final ConcurrentLinkedDeque<SupportFeedbackRecord> entries = new ConcurrentLinkedDeque<>();

    FeedbackRepository() {
        entries.add(new SupportFeedbackRecord(
                "fb-001",
                "cust-demo-001",
                "ord-0931",
                "DELAY",
                "雨の日に配送が15分遅れたという問い合わせ。",
                true,
                Instant.parse("2026-04-20T10:15:00Z").toString()));
        entries.add(new SupportFeedbackRecord(
                "fb-002",
                "cust-family-001",
                "ord-0924",
                "QUALITY",
                "ポテトが冷めていたという品質問い合わせ。",
                true,
                Instant.parse("2026-04-19T08:00:00Z").toString()));
        entries.add(new SupportFeedbackRecord(
                "fb-003",
                "cust-solo-001",
                "ord-0902",
                "SATISFACTION",
                "配達員の案内が丁寧だったという満足フィードバック。",
                false,
                Instant.parse("2026-04-18T12:30:00Z").toString()));
    }

    SupportFeedbackRecord record(String customerId, SupportFeedbackRequest request) {
        String classification = classify(request.message(), request.rating());
        boolean escalationRequired = needsEscalation(classification, request.rating(), request.message());
        SupportFeedbackRecord record = new SupportFeedbackRecord(
                "fb-" + Long.toHexString(System.nanoTime()),
                customerId,
                Objects.requireNonNullElse(request.orderId(), ""),
                classification,
                Objects.requireNonNullElse(request.message(), ""),
                escalationRequired,
                Instant.now().toString());
        entries.addFirst(record);
        return record;
    }

    List<FeedbackInsight> lookup(String query, int limit) {
        LinkedHashSet<String> terms = terms(query);
        return entries.stream()
                .map(entry -> new RankedFeedback(entry, score(entry, terms)))
                .filter(rank -> rank.score() > 0)
                .sorted(Comparator.comparingInt(RankedFeedback::score).reversed()
                        .thenComparing(rank -> rank.entry().feedbackId()))
                .map(rank -> new FeedbackInsight(
                        rank.entry().feedbackId(),
                        rank.entry().orderId(),
                        rank.entry().classification(),
                        rank.entry().summary(),
                        rank.entry().escalationRequired(),
                        rank.entry().createdAt()))
                .limit(limit)
                .toList();
    }

    private int score(SupportFeedbackRecord entry, LinkedHashSet<String> terms) {
        String haystack = (entry.classification() + " " + entry.summary()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (haystack.contains(term)) {
                score += 10;
            }
        }
        if (terms.contains("問い合わせ") || terms.contains("クレーム")) {
            score += 5;
        }
        return score;
    }

    private String classify(String message, Integer rating) {
        String lowered = Objects.requireNonNullElse(message, "").toLowerCase(Locale.ROOT);
        if (lowered.contains("遅") || lowered.contains("delay") || lowered.contains("届か")) {
            return "DELAY";
        }
        if (lowered.contains("冷め") || lowered.contains("品質") || lowered.contains("こぼ")) {
            return "QUALITY";
        }
        if (lowered.contains("違") || lowered.contains("誤") || lowered.contains("missing")) {
            return "WRONG_DELIVERY";
        }
        if (rating != null && rating >= 4) {
            return "SATISFACTION";
        }
        return "GENERAL";
    }

    private boolean needsEscalation(String classification, Integer rating, String message) {
        if (Objects.requireNonNullElse(message, "").contains("返金")) {
            return true;
        }
        if (rating != null && rating <= 2) {
            return true;
        }
        return switch (classification) {
            case "DELAY", "QUALITY", "WRONG_DELIVERY" -> true;
            default -> false;
        };
    }

    private LinkedHashSet<String> terms(String text) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String lowered = Objects.requireNonNullElse(text, "").toLowerCase(Locale.ROOT);
        for (String token : lowered.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+")) {
            if (!token.isBlank()) {
                terms.add(token);
            }
        }
        if (lowered.contains("問い合わせ") || lowered.contains("クレーム")) {
            terms.add("問い合わせ");
        }
        if (lowered.contains("遅")) {
            terms.add("delay");
            terms.add("遅延");
        }
        if (terms.isEmpty()) {
            terms.add("問い合わせ");
        }
        return terms;
    }

    private record RankedFeedback(SupportFeedbackRecord entry, int score) {
    }
}

@Component
class SupportStatusGateway {

    private final RestClient restClient;

    SupportStatusGateway(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl) {
        this.restClient = registryBaseUrl.isBlank() ? null : restClientBuilder.baseUrl(registryBaseUrl).build();
    }

    List<ServiceHealthSummary> currentStatuses() {
        if (restClient == null) {
            return fallback();
        }
        try {
            RegistryHealthResponsePayload response = restClient.get()
                    .uri("/registry/health")
                    .retrieve()
                    .body(RegistryHealthResponsePayload.class);
            if (response == null || response.services() == null) {
                return fallback();
            }
            return response.services().stream()
                    .filter(Objects::nonNull)
                    .map(service -> new ServiceHealthSummary(
                            service.serviceName(),
                            service.status(),
                            Objects.requireNonNullElse(service.healthEndpoint(), "")))
                    .toList();
        } catch (Exception ignored) {
            return fallback();
        }
    }

    private List<ServiceHealthSummary> fallback() {
        return List.of(
                new ServiceHealthSummary("support-service", "AVAILABLE", ""),
                new ServiceHealthSummary("registry-service", "UNKNOWN", ""));
    }
}

@Component
class OrderHistoryGateway {

    private final RestClient.Builder restClientBuilder;
    private final RestClient registryRestClient;
    private final String orderServiceName;
    private final String fallbackOrderServiceBaseUrl;
    private volatile String cachedOrderServiceBaseUrl;

    OrderHistoryGateway(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl,
            @Value("${ORDER_SERVICE_NAME:order-service}") String orderServiceName,
            @Value("${ORDER_SERVICE_BASE_URL:}") String orderServiceBaseUrl) {
        this.restClientBuilder = restClientBuilder;
        this.registryRestClient = registryBaseUrl.isBlank() ? null : restClientBuilder.baseUrl(registryBaseUrl).build();
        this.orderServiceName = orderServiceName;
        this.fallbackOrderServiceBaseUrl = orderServiceBaseUrl;
        this.cachedOrderServiceBaseUrl = "";
    }

    List<CustomerOrderHistoryEntry> recentOrders(String accessToken) {
        String orderHistoryUrl = joinUrl(resolveOrderServiceBaseUrl(), "/api/orders/history");
        if (!StringUtils.hasText(orderHistoryUrl) || accessToken == null || accessToken.isBlank()) {
            return List.of();
        }
        try {
            CustomerOrderHistoryEntry[] response = restClientBuilder.build().get()
                    .uri(orderHistoryUrl)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(CustomerOrderHistoryEntry[].class);
            return response == null ? List.of() : List.of(response);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String resolveOrderServiceBaseUrl() {
        if (StringUtils.hasText(cachedOrderServiceBaseUrl)) {
            return cachedOrderServiceBaseUrl;
        }
        String discoveredBaseUrl = discoverOrderServiceBaseUrl();
        if (StringUtils.hasText(discoveredBaseUrl)) {
            cachedOrderServiceBaseUrl = discoveredBaseUrl;
            return discoveredBaseUrl;
        }
        return fallbackOrderServiceBaseUrl;
    }

    private String discoverOrderServiceBaseUrl() {
        if (registryRestClient == null || !StringUtils.hasText(orderServiceName)) {
            return "";
        }
        try {
            RegistryServiceDescriptorPayload[] response = registryRestClient.get()
                    .uri("/registry/services")
                    .retrieve()
                    .body(RegistryServiceDescriptorPayload[].class);
            if (response == null) {
                return "";
            }
            return List.of(response).stream()
                    .filter(Objects::nonNull)
                    .filter(service -> orderServiceName.equalsIgnoreCase(service.serviceName()))
                    .filter(service -> "AVAILABLE".equalsIgnoreCase(service.status()))
                    .map(RegistryServiceDescriptorPayload::endpoint)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String joinUrl(String endpoint, String requestPath) {
        if (!StringUtils.hasText(endpoint)) {
            return "";
        }
        if (!StringUtils.hasText(requestPath)) {
            return endpoint;
        }
        if (requestPath.startsWith("http://") || requestPath.startsWith("https://")) {
            return requestPath;
        }
        if (endpoint.endsWith("/") && requestPath.startsWith("/")) {
            return endpoint.substring(0, endpoint.length() - 1) + requestPath;
        }
        if (!endpoint.endsWith("/") && !requestPath.startsWith("/")) {
            return endpoint + "/" + requestPath;
        }
        return endpoint + requestPath;
    }
}

@Component
class OrderHistorySnapshotStore {

    private final Map<String, List<CustomerOrderHistoryEntry>> snapshots = new LinkedHashMap<>();

    synchronized void cache(String customerId, List<CustomerOrderHistoryEntry> orders) {
        snapshots.put(customerId, List.copyOf(orders));
    }

    synchronized List<CustomerOrderHistoryEntry> get(String customerId) {
        return snapshots.getOrDefault(customerId, List.of());
    }
}

@Configuration
class SupportArachneConfiguration {

    @Bean
    Tool faqLookupTool(FaqRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode properties = root.putObject("properties");
                properties.putObject("query")
                        .put("type", "string")
                        .put("description", "FAQ を検索する問い合わせ文");
                properties.putObject("limit")
                        .put("type", "integer")
                        .put("description", "返す件数の上限");
                root.putArray("required").add("query");
                root.put("additionalProperties", false);
                return new ToolSpec("faq_lookup", "FAQナレッジを検索して回答候補を返す。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("faq_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                String query = String.valueOf(values.getOrDefault("query", ""));
                int limit = Integer.parseInt(String.valueOf(values.getOrDefault("limit", 3)));
                List<Map<String, Object>> matches = repository.lookup(query, limit).stream()
                        .<Map<String, Object>>map(entry -> {
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            mapped.put("id", entry.id());
                            mapped.put("question", entry.question());
                            mapped.put("answer", entry.answer());
                            mapped.put("tags", entry.tags());
                            return mapped;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("matches", matches));
            }
        };
    }

    @Bean
    Tool campaignLookupTool(CampaignRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                root.putObject("properties");
                root.put("additionalProperties", false);
                return new ToolSpec("campaign_lookup", "現在有効なキャンペーンを返す。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("campaign_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                List<Map<String, Object>> campaigns = repository.activeCampaigns().stream()
                        .<Map<String, Object>>map(campaign -> {
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            mapped.put("campaignId", campaign.campaignId());
                            mapped.put("title", campaign.title());
                            mapped.put("description", campaign.description());
                            mapped.put("badge", campaign.badge());
                            mapped.put("validUntil", campaign.validUntil());
                            return mapped;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("campaigns", campaigns));
            }
        };
    }

    @Bean
    Tool serviceStatusLookupTool(SupportStatusGateway gateway) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                root.putObject("properties");
                root.put("additionalProperties", false);
                return new ToolSpec("service_status_lookup", "registry-service 経由で現在の稼働状況を取得する。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("service_status_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                List<Map<String, Object>> services = gateway.currentStatuses().stream()
                        .<Map<String, Object>>map(service -> {
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            mapped.put("serviceName", service.serviceName());
                            mapped.put("status", service.status());
                            mapped.put("healthEndpoint", service.healthEndpoint());
                            return mapped;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("services", services));
            }
        };
    }

    @Bean
    Tool feedbackLookupTool(FeedbackRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode properties = root.putObject("properties");
                properties.putObject("query")
                        .put("type", "string")
                        .put("description", "過去問い合わせを検索する条件");
                properties.putObject("limit")
                        .put("type", "integer")
                        .put("description", "返す件数の上限");
                root.putArray("required").add("query");
                root.put("additionalProperties", false);
                return new ToolSpec("feedback_lookup", "過去の問い合わせ・フィードバックを検索する。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("feedback_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                String query = String.valueOf(values.getOrDefault("query", ""));
                int limit = Integer.parseInt(String.valueOf(values.getOrDefault("limit", 3)));
                List<Map<String, Object>> entries = repository.lookup(query, limit).stream()
                        .<Map<String, Object>>map(entry -> {
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            mapped.put("feedbackId", entry.feedbackId());
                            mapped.put("orderId", entry.orderId());
                            mapped.put("category", entry.category());
                            mapped.put("summary", entry.summary());
                            mapped.put("escalationRequired", entry.escalationRequired());
                            mapped.put("createdAt", entry.createdAt());
                            return mapped;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("entries", entries));
            }
        };
    }

    @Bean
    Tool orderHistoryLookupTool(OrderHistorySnapshotStore snapshotStore) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode properties = root.putObject("properties");
                properties.putObject("customerId")
                        .put("type", "string")
                        .put("description", "注文履歴を参照する顧客ID");
                root.putArray("required").add("customerId");
                root.put("additionalProperties", false);
                return new ToolSpec("order_history_lookup", "認証済みカスタマーの直近注文履歴を取得する。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("order_history_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                String customerId = String.valueOf(values.getOrDefault("customerId", ""));
                List<Map<String, Object>> orders = snapshotStore.get(customerId).stream()
                        .<Map<String, Object>>map(order -> {
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            mapped.put("orderId", order.orderId());
                            mapped.put("itemSummary", order.itemSummary());
                            mapped.put("total", order.total());
                            mapped.put("etaLabel", order.etaLabel());
                            mapped.put("paymentStatus", order.paymentStatus());
                            mapped.put("createdAt", order.createdAt());
                            return mapped;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("orders", orders));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.model.mode", havingValue = "deterministic", matchIfMissing = false)
    Model supportDeterministicModel() {
        return new SupportDeterministicModel();
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

final class SupportDeterministicModel implements Model {

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
        String query = latestUserText(messages);
        SupportIntent intent = SupportIntent.fromMessage(query);
        if (intent.includesCampaigns() && latestToolContent(messages, "campaign-lookup") == null) {
            return List.of(
                    new ModelEvent.ToolUse("campaign-lookup", "campaign_lookup", Map.of()),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }
        if (intent.includesStatuses() && latestToolContent(messages, "status-lookup") == null) {
            return List.of(
                    new ModelEvent.ToolUse("status-lookup", "service_status_lookup", Map.of()),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }
        if (intent.includesOrderHistory() && latestToolContent(messages, "history-lookup") == null) {
            return List.of(
                new ModelEvent.ToolUse(
                    "history-lookup",
                    "order_history_lookup",
                    Map.of("customerId", extractCustomerId(query))),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }
        if (intent.includesFeedback() && latestToolContent(messages, "feedback-lookup") == null) {
            return List.of(
                    new ModelEvent.ToolUse("feedback-lookup", "feedback_lookup", Map.of("query", query, "limit", 3)),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }
        if (intent.includesFaq() && latestToolContent(messages, "faq-lookup") == null) {
            return List.of(
                    new ModelEvent.ToolUse("faq-lookup", "faq_lookup", Map.of("query", query, "limit", 3)),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        String summary = buildSummary(
                query,
                latestToolContent(messages, "faq-lookup"),
                latestToolContent(messages, "campaign-lookup"),
                latestToolContent(messages, "status-lookup"),
                latestToolContent(messages, "history-lookup"),
                latestToolContent(messages, "feedback-lookup"));
        return List.of(
                new ModelEvent.TextDelta(summary),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
    }

    private String buildSummary(
            String query,
            Map<String, Object> faqResult,
            Map<String, Object> campaignResult,
            Map<String, Object> statusResult,
            Map<String, Object> historyResult,
            Map<String, Object> feedbackResult) {
        List<String> sections = new ArrayList<>();
        List<String> faqAnswers = extractStrings(faqResult, "matches", "answer");
        if (!faqAnswers.isEmpty()) {
            sections.add("FAQ: " + faqAnswers.getFirst());
        }
        List<String> campaigns = extractStrings(campaignResult, "campaigns", "title");
        if (!campaigns.isEmpty()) {
            sections.add("キャンペーン: " + String.join("、", campaigns));
        }
        List<String> statuses = extractPairs(statusResult, "services", "serviceName", "status");
        if (!statuses.isEmpty()) {
            sections.add("稼働状況: " + String.join("、", statuses));
        }
        List<String> orders = extractStrings(historyResult, "orders", "itemSummary");
        if (!orders.isEmpty()) {
            sections.add("直近注文: " + orders.getFirst());
        }
        List<String> feedback = extractStrings(feedbackResult, "entries", "category");
        if (!feedback.isEmpty()) {
            sections.add("類似問い合わせ: " + String.join("、", feedback));
        }
        if (sections.isEmpty()) {
            sections.add("お問い合わせを受け付けました。状況や注文番号が分かれば追加で案内できます。");
        }
        HandoffInstruction handoff = HandoffInstruction.fromMessage(query);
        if (!handoff.target().isBlank()) {
            sections.add("[HANDOFF: " + handoff.target() + "]\n" + handoff.message());
        }
        return String.join(" ", sections);
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

    private String extractCustomerId(String query) {
        String marker = "customerId=";
        int markerIndex = query.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        int start = markerIndex + marker.length();
        int end = start;
        while (end < query.length()) {
            char current = query.charAt(end);
            if (Character.isWhitespace(current) || current == '。' || current == ',' || current == ';') {
                break;
            }
            end++;
        }
        return query.substring(start, end);
    }

    private List<String> extractStrings(Map<String, Object> toolResult, String listKey, String valueKey) {
        if (toolResult == null) {
            return List.of();
        }
        Object rawItems = toolResult.get(listKey);
        if (!(rawItems instanceof List<?> items)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> entry) {
                Object value = entry.get(valueKey);
                if (value != null && !String.valueOf(value).isBlank()) {
                    values.add(String.valueOf(value));
                }
            }
        }
        return values;
    }

    private List<String> extractPairs(Map<String, Object> toolResult, String listKey, String leftKey, String rightKey) {
        if (toolResult == null) {
            return List.of();
        }
        Object rawItems = toolResult.get(listKey);
        if (!(rawItems instanceof List<?> items)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> entry) {
                Object left = entry.get(leftKey);
                Object right = entry.get(rightKey);
                if (left != null && right != null) {
                    values.add(left + "=" + right);
                }
            }
        }
        return values;
    }
}

final class SecurityAccessors {

    private SecurityAccessors() {
    }

    static String currentCustomerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getSubject();
        }
        throw new IllegalStateException("No authenticated customer is available in the security context");
    }

    static String requiredAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getTokenValue();
        }
        throw new IllegalStateException("No access token is available in the security context");
    }
}

record SupportIntent(
        boolean includesFaq,
        boolean includesCampaigns,
        boolean includesStatuses,
        boolean includesOrderHistory,
        boolean includesFeedback) {

    static SupportIntent fromMessage(String message) {
        String lowered = Objects.requireNonNullElse(message, "").toLowerCase(Locale.ROOT);
        boolean campaigns = containsAny(lowered, "キャンペーン", "クーポン", "割引", "特典", "ポイント");
        boolean statuses = containsAny(lowered, "稼働", "status", "状態", "配送状況", "営業", "available");
        boolean orderHistory = containsAny(lowered, "注文履歴", "履歴", "前回", "最近の注文", "いつもの");
        boolean feedback = containsAny(lowered, "問い合わせ", "クレーム", "苦情", "遅", "冷め", "違う", "誤配送");
        boolean faq = containsAny(lowered, "faq", "よくある", "支払い", "キャンセル", "注文方法", "配送")
                || (!campaigns && !statuses && !orderHistory && !feedback);
        return new SupportIntent(faq, campaigns, statuses, orderHistory, feedback);
    }

    private static boolean containsAny(String source, String... candidates) {
        for (String candidate : candidates) {
            if (source.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}

record HandoffInstruction(String target, String message) {

    static HandoffInstruction fromMessage(String message) {
        String lowered = Objects.requireNonNullElse(message, "").toLowerCase(Locale.ROOT);
        if (lowered.contains("注文") && (lowered.contains("変更") || lowered.contains("再注文") || lowered.contains("キャンセル") || lowered.contains("したい"))) {
            return new HandoffInstruction("order", "注文内容の変更や再注文は order-service の注文ワークフローへ引き継ぎます。");
        }
        return new HandoffInstruction("", "");
    }
}

record SupportChatRequest(String sessionId, String message) {
}

record SupportFeedbackRequest(String orderId, Integer rating, String message) {
}

record SupportChatResponse(
        String sessionId,
        String service,
        String agent,
        String headline,
        String summary,
        List<FaqEntry> faqMatches,
        List<CampaignSummary> campaigns,
        List<ServiceHealthSummary> serviceStatuses,
        List<CustomerOrderHistoryEntry> recentOrders,
        List<FeedbackInsight> relatedFeedback,
        String handoffTarget,
        String handoffMessage) {
}

record SupportFeedbackResponse(
        String service,
        String agent,
        String headline,
        String summary,
        String classification,
        boolean escalationRequired) {
}

record SupportStatusResponse(String service, String agent, String summary, List<ServiceHealthSummary> services) {
}

record FaqEntry(String id, String question, String answer, List<String> tags) {
}

record CampaignSummary(String campaignId, String title, String description, String badge, String validUntil) {
}

record ServiceHealthSummary(String serviceName, String status, String healthEndpoint) {
}

record CustomerOrderHistoryEntry(
        String orderId,
        String itemSummary,
        BigDecimal total,
        String etaLabel,
        String paymentStatus,
        String createdAt) {
}

record FeedbackInsight(
        String feedbackId,
        String orderId,
        String category,
        String summary,
        boolean escalationRequired,
        String createdAt) {
}

record SupportFeedbackRecord(
        String feedbackId,
        String customerId,
        String orderId,
        String classification,
        String summary,
        boolean escalationRequired,
        String createdAt) {
}

record RegistryHealthResponsePayload(List<RegistryHealthEntryPayload> services) {
}

record RegistryHealthEntryPayload(String serviceName, String status, String healthEndpoint) {
}

record RegistryServiceDescriptorPayload(String serviceName, String endpoint, String status) {
}