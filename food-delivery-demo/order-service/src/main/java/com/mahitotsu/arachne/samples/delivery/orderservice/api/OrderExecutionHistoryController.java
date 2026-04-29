package com.mahitotsu.arachne.samples.delivery.orderservice.api;

import static com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderExecutionHistoryTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure.OrderExecutionHistoryStore;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/order", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Order Execution History", description = "注文ワークフロー session ごとの実行履歴を照会するエンドポイントです。")
public class OrderExecutionHistoryController {

    private final OrderExecutionHistoryStore historyStore;

    OrderExecutionHistoryController(OrderExecutionHistoryStore historyStore) {
        this.historyStore = historyStore;
    }

    @Operation(
            summary = "Read execution history",
            description = "指定したワークフロー session ID に紐づく workflow / downstream 実行履歴を時系列で返します。")
    @GetMapping("/execution-history/{sessionId}")
    OrderExecutionHistoryResponse executionHistory(@PathVariable String sessionId) {
        return historyStore.history(sessionId);
    }
}