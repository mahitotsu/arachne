package com.mahitotsu.arachne.samples.delivery.deliveryservice.api;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryExecutionHistoryTypes.*;
import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.deliveryservice.application.DeliveryApplicationService;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.observation.DeliveryExecutionHistoryStore;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/internal/delivery", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Delivery Service", description = "配送見積もりとレーン順位付けを行う delivery-service のエンドポイントです。")
public class DeliveryController {

    private final DeliveryApplicationService applicationService;
    private final DeliveryExecutionHistoryStore historyStore;

    DeliveryController(DeliveryApplicationService applicationService, DeliveryExecutionHistoryStore historyStore) {
        this.applicationService = applicationService;
        this.historyStore = historyStore;
    }

    @PostMapping("/quote")
    @Operation(
            summary = "Quote delivery options",
            description = "顧客の配送意図と商品名を受け取り、自社配送と外部配送を順位付きで返します。",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "delivery-agent"),
                @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"preference\",\"meaning\":\"配送希望。priority と rawMessage を使って速さ・価格の優先度や補足を渡します。\"},{\"field\":\"itemNames\",\"meaning\":\"配送見積もり対象に含まれる商品名。\"}],\"optionalInputs\":[{\"field\":\"sessionId\",\"meaning\":\"親ワークフローの相関 ID。\"}]}", parseValue = true)
            }))
    DeliveryQuoteResponse quote(@RequestBody DeliveryQuoteRequest request) {
        return applicationService.quote(request);
    }

    @GetMapping("/execution-history/{sessionId}")
    @Operation(summary = "Read delivery execution history", description = "指定した session ID に紐づく delivery-agent の実行履歴を返します。")
    DeliveryExecutionHistoryResponse executionHistory(@PathVariable String sessionId) {
        return historyStore.history(sessionId);
    }
}