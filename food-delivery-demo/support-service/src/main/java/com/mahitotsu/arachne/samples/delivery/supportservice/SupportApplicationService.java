package com.mahitotsu.arachne.samples.delivery.supportservice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;

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