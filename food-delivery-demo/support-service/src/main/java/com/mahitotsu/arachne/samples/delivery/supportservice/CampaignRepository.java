package com.mahitotsu.arachne.samples.delivery.supportservice;

import java.util.List;

import org.springframework.stereotype.Component;

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