package com.mahitotsu.arachne.samples.delivery.idatenadapter;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

@Service
class IdatenAdapterService {

    AdapterEtaResponse quote(AdapterEtaRequest request) {
        int itemCount = request.itemNames() == null ? 0 : request.itemNames().size();
        return new AdapterEtaResponse(
                "idaten-adapter",
                "AVAILABLE",
                34 + itemCount,
                "low",
                new BigDecimal("180.00"),
                "Idaten は低価格配送を提供しています。");
    }
}