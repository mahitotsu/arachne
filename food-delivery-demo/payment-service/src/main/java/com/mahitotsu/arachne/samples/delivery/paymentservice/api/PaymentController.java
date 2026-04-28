package com.mahitotsu.arachne.samples.delivery.paymentservice.api;

import static com.mahitotsu.arachne.samples.delivery.paymentservice.domain.PaymentTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.paymentservice.application.PaymentApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/internal/payment", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Payment Service", description = "決定論的な支払い準備と任意の課金実行を行う payment-service のエンドポイントです。")
public class PaymentController {

    private final PaymentApplicationService applicationService;

    PaymentController(PaymentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/prepare")
    @Operation(
            summary = "Prepare payment",
            description = "チェックアウト意図と合計金額を受け取り、準備済みの支払い状態と必要に応じて課金結果を返します。",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "payment-service"),
                @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"instruction\",\"meaning\":\"支払い方法の希望。requestedMethod と rawMessage を使って決定論的な支払い準備に必要な情報を渡します。\"},{\"field\":\"total\",\"meaning\":\"payment-service が準備または課金する決定論的な合計金額。\"}],\"optionalInputs\":[{\"field\":\"confirmRequested\",\"meaning\":\"支払い準備のみ行うか、実際に課金まで行うか。\"},{\"field\":\"sessionId\",\"meaning\":\"親ワークフローの相関 ID。\"}]}", parseValue = true)
            }))
    PaymentPrepareResponse prepare(@RequestBody PaymentPrepareRequest request) {
        return applicationService.prepare(request);
    }
}