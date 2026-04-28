package com.mahitotsu.arachne.samples.delivery.deliveryservice.api;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.deliveryservice.application.DeliveryApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/internal/delivery", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Delivery Service", description = "配送見積もりとレーン順位付けを行う delivery-service のエンドポイントです。")
public class DeliveryController {

    private final DeliveryApplicationService applicationService;

    DeliveryController(DeliveryApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/quote")
    @Operation(
            summary = "Quote delivery options",
            description = "顧客の配送意図と商品名を受け取り、自社配送と外部配送を順位付きで返します。",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "delivery-agent"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"message\",\"meaning\":\"速さや価格などに関する自然言語の配送希望。\"},{\"field\":\"itemNames\",\"meaning\":\"配送見積もり対象に含まれる商品名。\"}],\"optionalInputs\":[{\"field\":\"sessionId\",\"meaning\":\"親ワークフローの相関 ID。\"}]}", parseValue = true)
            }))
    DeliveryQuoteResponse quote(@RequestBody DeliveryQuoteRequest request) {
        return applicationService.quote(request);
    }
}