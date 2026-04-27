package com.mahitotsu.arachne.samples.delivery.hermesadapter;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

@Service
class HermesAdapterService {

    boolean available() {
        return (System.currentTimeMillis() / 15000) % 3 != 0;
    }

    AdapterEtaResponse quote(AdapterEtaRequest request) {
        if (!available()) {
            return new AdapterEtaResponse("hermes-adapter", "NOT_AVAILABLE", 0, "high", new BigDecimal("0.00"),
                    "Hermes は現在混雑のため受付停止中です。");
        }
        int itemCount = request.itemNames() == null ? 0 : request.itemNames().size();
        return new AdapterEtaResponse(
                "hermes-adapter",
                "AVAILABLE",
                22 + itemCount,
                itemCount > 2 ? "high" : "medium",
                new BigDecimal("350.00"),
                "Hermes は高速配送を提供しています。");
    }
}