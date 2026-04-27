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

import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;

@Service
public class DeliveryApplicationService {

    private static final String ETA_DISCOVERY_QUERY = "外部ETAを提供するサービスは？";
    private static final String DELIVERY_PROMPT = """
            あなたは単一キッチンのクラウドキッチンアプリの delivery-agent です。
            必ず次の順でツールを使ってください:
            1. check_courier_availability
            2. get_traffic_weather
            3. discover_eta_services
            4. discover で返った各 AVAILABLE サービスに対して call_eta_service
            回答は日本語で、次のトレースを簡潔に書いてください:
            - 自社スタッフ確認
            - 交通・天候確認
            - registry discovery の結果
            - 各外部 ETA API 呼び出し結果
            - 推奨オプションと理由
            「急いで」「最速」は最短 ETA を優先し、「安く」「節約」は最安料金を優先してください。
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

    DeliveryApplicationService(
            AgentFactory agentFactory,
            CourierAvailabilityRepository courierRepository,
            TrafficWeatherRepository trafficWeatherRepository,
            EtaServiceDiscoveryGateway etaServiceDiscoveryGateway,
            ExternalEtaGateway externalEtaGateway,
            Tool courierAvailabilityTool,
            Tool trafficWeatherTool,
            Tool discoverEtaServicesTool,
            Tool callEtaServiceTool) {
        this.agentFactory = agentFactory;
        this.courierRepository = courierRepository;
        this.trafficWeatherRepository = trafficWeatherRepository;
        this.etaServiceDiscoveryGateway = etaServiceDiscoveryGateway;
        this.externalEtaGateway = externalEtaGateway;
        this.courierAvailabilityTool = courierAvailabilityTool;
        this.trafficWeatherTool = trafficWeatherTool;
        this.discoverEtaServicesTool = discoverEtaServicesTool;
        this.callEtaServiceTool = callEtaServiceTool;
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
        DeliveryRanking ranking = DeliveryRankingPolicy.rank(candidateOptions, request.message());

        String summary = agentFactory.builder()
                .systemPrompt(DELIVERY_PROMPT)
                .tools(courierAvailabilityTool, trafficWeatherTool, discoverEtaServicesTool, callEtaServiceTool)
                .build()
                .run("注文の配送状況を調査してください: " + request.message()
                        + "。アイテム: " + request.itemNames()
                        + "。推奨候補: " + ranking.recommendedTier()
                        + "。推奨理由: " + ranking.recommendationReason())
                .text();

        String headline = ranking.recommendedTier().isBlank()
                ? "delivery-agent が利用可能な配送候補を確認できませんでした"
                : "delivery-agent が " + labelFor(ranking.recommendedTier(), ranking.options()) + " を推奨しました";

        return new DeliveryQuoteResponse(
                "delivery-service",
                "delivery-agent",
                headline,
                summary,
                ranking.options(),
                ranking.recommendedTier(),
                ranking.recommendationReason());
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