package com.mahitotsu.arachne.samples.delivery.idatenadapter.application;

import static com.mahitotsu.arachne.samples.delivery.idatenadapter.domain.IdatenAdapterTypes.*;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

@Service
public class IdatenAdapterService {

    public AdapterEtaResponse quote(AdapterEtaRequest request) {
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