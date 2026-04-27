package com.mahitotsu.arachne.samples.delivery.supportservice.api;

import java.util.List;

import com.mahitotsu.arachne.samples.delivery.supportservice.domain.CampaignSummary;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.CustomerOrderHistoryEntry;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.FaqEntry;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.FeedbackInsight;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.ServiceHealthSummary;

public record SupportChatResponse(
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