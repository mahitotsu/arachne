package com.mahitotsu.arachne.samples.delivery.orderservice.api;

import static com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.orderservice.application.OrderApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/order", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Order Workflow", description = "order-service が公開する注文ワークフローのエンドポイントです。")
public class OrderController {

    private final OrderApplicationService applicationService;

    OrderController(OrderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Operation(
            summary = "Suggest order items",
            description = "現在の注文意図または追加要望を受け取り、提案商品、ETA、ワークフロー状態、サービス trace を返します。",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "menu-agent"),
                @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"message\",\"meaning\":\"このターンの自然言語の注文意図。\"}],\"optionalInputs\":[{\"field\":\"refinement\",\"meaning\":\"前回提案に対する追加制約。\"},{\"field\":\"locale\",\"meaning\":\"応答言語のヒント。\"}],\"serviceBehavior\":\"message から再注文意図が読み取れる場合、order-service は downstream の menu prompt に最近の注文コンテキストを補うことがあります。\"}", parseValue = true)
            }))
    @PostMapping(path = "/suggest", consumes = MediaType.APPLICATION_JSON_VALUE)
    SuggestOrderResponse suggest(@RequestBody SuggestOrderRequest request) {
        return applicationService.suggest(request);
    }

    @Operation(
            summary = "Confirm selected items",
            description = "選択済みの提案商品を受け取り、更新後の注文下書きと配送候補を返します。")
    @PostMapping(path = "/confirm-items", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfirmItemsResponse confirmItems(@RequestBody ConfirmItemsRequest request) {
        return applicationService.confirmItems(request);
    }

    @Operation(
            summary = "Confirm delivery lane",
            description = "選択した配送コードを受け取り、支払い要約と更新後の注文下書きを返します。")
    @PostMapping(path = "/confirm-delivery", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfirmDeliveryResponse confirmDelivery(@RequestBody ConfirmDeliveryRequest request) {
        return applicationService.confirmDelivery(request);
    }

    @Operation(
            summary = "Confirm payment",
            description = "準備済みの支払いを使って注文を確定し、確定済み下書きと service trace を返します。")
    @PostMapping(path = "/confirm-payment", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfirmPaymentResponse confirmPayment(@RequestBody ConfirmPaymentRequest request) {
        return applicationService.confirmPayment(request);
    }

    @Operation(
            summary = "Restore order session",
            description = "セッションに対して現在のワークフロー段階、保留中の提案、配送候補、注文下書きを復元します。")
    @GetMapping("/session/{sessionId}")
    OrderSessionView session(@PathVariable String sessionId) {
        return applicationService.session(sessionId);
    }
}