package com.mahitotsu.arachne.samples.delivery.deliveryservice.application;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure.CourierAvailabilityRepository;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure.EtaServiceDiscoveryGateway;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure.ExternalEtaGateway;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure.TrafficWeatherRepository;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.observation.AgentObservationSupport;

import com.mahitotsu.arachne.strands.agent.AgentResult;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;

@Service
public class DeliveryApplicationService {

    private static final String ETA_DISCOVERY_QUERY = "外部ETAを提供するサービスは？";
    private static final String DELIVERY_PROMPT = """
            あなたは単一キッチンのクラウドキッチンアプリの delivery-agent です。
            delivery-routing を有効化し、必ず次の順でツールを使ってください:
            1. check_courier_availability
            2. get_traffic_weather
            3. discover_eta_services
            4. discover で返った各 AVAILABLE サービスに対して call_eta_service
            返ってきていない外部サービスを推測して追加してはいけません。AVAILABLE でない候補を比較に含めないでください。
            このエージェントの責務は配送候補の比較と推奨までです。配達の確約、注文確定、クーリエ手配完了の断定はしないでください。
            回答は日本語で、次のトレースを簡潔に書いてください:
            - 自社スタッフ確認
            - 交通・天候確認
            - registry discovery の結果
            - 各外部 ETA API 呼び出し結果
            - 推奨オプションと理由
            「急いで」「最速」は最短 ETA を優先し、「安く」「節約」は最安料金を優先してください。
            どちらでもない場合は ETA と料金のバランスで説明してください。
            最終回答は structured_output を使い、summary, recommendedTier, recommendationReason を返してください。
            """;

    private final AgentFactory agentFactory;
    private final CourierAvailabilityRepository courierRepository;
    private final TrafficWeatherRepository trafficWeatherRepository;
    private final EtaServiceDiscoveryGateway etaServiceDiscoveryGateway;
    private final ExternalEtaGateway externalEtaGateway;
    private final Tool courierAvailabilityTool;
    private final Tool trafficWeatherTool;
    private final Tool discoverEtaServicesTool;
    private final Tool callEtaServiceTool;
    private final AgentObservationSupport agentObservationSupport;

    DeliveryApplicationService(
            AgentFactory agentFactory,
            CourierAvailabilityRepository courierRepository,
            TrafficWeatherRepository trafficWeatherRepository,
            EtaServiceDiscoveryGateway etaServiceDiscoveryGateway,
            ExternalEtaGateway externalEtaGateway,
            Tool courierAvailabilityTool,
            Tool trafficWeatherTool,
            Tool discoverEtaServicesTool,
            Tool callEtaServiceTool,
            AgentObservationSupport agentObservationSupport) {
        this.agentFactory = agentFactory;
        this.courierRepository = courierRepository;
        this.trafficWeatherRepository = trafficWeatherRepository;
        this.etaServiceDiscoveryGateway = etaServiceDiscoveryGateway;
        this.externalEtaGateway = externalEtaGateway;
        this.courierAvailabilityTool = courierAvailabilityTool;
        this.trafficWeatherTool = trafficWeatherTool;
        this.discoverEtaServicesTool = discoverEtaServicesTool;
        this.callEtaServiceTool = callEtaServiceTool;
        this.agentObservationSupport = agentObservationSupport;
    }

    public DeliveryQuoteResponse quote(DeliveryQuoteRequest request) {
        CourierStatus courierStatus = courierRepository.check(request.itemNames());
        TrafficWeatherStatus conditions = trafficWeatherRepository.current();
        List<EtaServiceTarget> etaServices = etaServiceDiscoveryGateway.discoverAvailableEtaServices(ETA_DISCOVERY_QUERY);
        List<ExternalEtaQuote> externalQuotes = etaServices.stream()
                .map(service -> externalEtaGateway.quote(service, request.itemNames(), request.message()))
                .flatMap(Optional::stream)
                .toList();
        List<DeliveryOption> candidateOptions = buildCandidateOptions(courierStatus, conditions, externalQuotes);
        DeliveryRanking fallbackRanking = DeliveryRankingPolicy.rank(candidateOptions, request.message());

        AgentResult decisionResult = agentObservationSupport.observe("delivery-service", "delivery-agent", () -> agentFactory.builder()
            .systemPrompt(DELIVERY_PROMPT)
            .tools(courierAvailabilityTool, trafficWeatherTool, discoverEtaServicesTool, callEtaServiceTool)
            .build()
            .run("注文の配送状況を調査してください: " + request.message() + "。アイテム: " + request.itemNames(), DeliveryDecision.class));
        DeliveryDecision decision = decisionResult.structuredOutput(DeliveryDecision.class);

        DeliveryDecision effectiveDecision = validateDecision(decision, candidateOptions, fallbackRanking);

        String headline = effectiveDecision.recommendedTier().isBlank()
                ? "delivery-agent が利用可能な配送候補を確認できませんでした"
            : "delivery-agent が " + labelFor(effectiveDecision.recommendedTier(), fallbackRanking.options()) + " を推奨しました";

        return new DeliveryQuoteResponse(
                "delivery-service",
                "delivery-agent",
                headline,
            effectiveDecision.summary(),
                fallbackRanking.options(),
            effectiveDecision.recommendedTier(),
            effectiveDecision.recommendationReason());
    }

        private DeliveryDecision validateDecision(
            DeliveryDecision decision,
            List<DeliveryOption> candidateOptions,
            DeliveryRanking fallbackRanking) {
        if (decision == null) {
            return new DeliveryDecision(
                fallbackRanking.recommendationReason(),
                fallbackRanking.recommendedTier(),
                fallbackRanking.recommendationReason());
        }
        boolean knownTier = candidateOptions.stream().anyMatch(option -> option.code().equals(decision.recommendedTier()));
        if (!knownTier) {
            return new DeliveryDecision(
                decision.summary() == null || decision.summary().isBlank()
                    ? fallbackRanking.recommendationReason()
                    : decision.summary(),
                fallbackRanking.recommendedTier(),
                fallbackRanking.recommendationReason());
        }
        String summary = decision.summary() == null || decision.summary().isBlank()
            ? fallbackRanking.recommendationReason()
            : decision.summary();
        String reason = decision.recommendationReason() == null || decision.recommendationReason().isBlank()
            ? fallbackRanking.recommendationReason()
            : decision.recommendationReason();
        return new DeliveryDecision(summary, decision.recommendedTier(), reason);
        }

    private List<DeliveryOption> buildCandidateOptions(
            CourierStatus courierStatus,
            TrafficWeatherStatus conditions,
            List<ExternalEtaQuote> externalQuotes) {
        int trafficDelay = conditions.trafficDelayMinutes();
        int weatherDelay = conditions.weatherDelayMinutes();
        int expressBaseEta = 15 + trafficDelay / 2 + weatherDelay / 2;

        List<DeliveryOption> options = new ArrayList<>();
        if (courierStatus.expressAvailable()) {
            options.add(new DeliveryOption("express", "自社エクスプレス", expressBaseEta, new BigDecimal("300.00")));
        }
        for (ExternalEtaQuote quote : externalQuotes) {
            options.add(new DeliveryOption(
                    externalCode(quote.serviceName()),
                    externalLabel(quote.serviceName()),
                    quote.etaMinutes(),
                    quote.fee()));
        }
        return options;
    }

    private String externalCode(String serviceName) {
        return switch (serviceName) {
            case "hermes-adapter" -> "hermes";
            case "idaten-adapter" -> "idaten";
            default -> serviceName.replace("-adapter", "");
        };
    }

    private String externalLabel(String serviceName) {
        return switch (serviceName) {
            case "hermes-adapter" -> "Hermes スピード便";
            case "idaten-adapter" -> "Idaten エコノミー";
            default -> serviceName;
        };
    }

    private String labelFor(String code, List<DeliveryOption> options) {
        return options.stream()
                .filter(option -> option.code().equals(code))
                .map(DeliveryOption::label)
                .findFirst()
                .orElse(code);
    }
}