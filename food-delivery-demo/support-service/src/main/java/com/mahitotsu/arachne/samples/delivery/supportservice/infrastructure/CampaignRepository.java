package com.mahitotsu.arachne.samples.delivery.supportservice.infrastructure;

import java.util.List;

import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.samples.delivery.supportservice.domain.CampaignSummary;

@Component
public class CampaignRepository {

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

    public List<CampaignSummary> activeCampaigns() {
        return campaigns;
    }
}