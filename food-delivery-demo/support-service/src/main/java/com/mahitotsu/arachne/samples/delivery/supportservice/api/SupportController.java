package com.mahitotsu.arachne.samples.delivery.supportservice.api;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.supportservice.application.SupportApplicationService;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.CampaignSummary;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.SupportExecutionHistoryTypes.SupportExecutionHistoryResponse;
import com.mahitotsu.arachne.samples.delivery.supportservice.observation.SupportExecutionHistoryStore;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/support", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Support Service", description = "chat、feedback、campaign、稼働状況を扱う support-service のエンドポイントです。")
public class SupportController {

    private final SupportApplicationService applicationService;
    private final SupportExecutionHistoryStore historyStore;

    SupportController(SupportApplicationService applicationService, SupportExecutionHistoryStore historyStore) {
        this.applicationService = applicationService;
        this.historyStore = historyStore;
    }

    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Start a support chat turn",
            description = "認証済みの support 問い合わせを受け取り、FAQ、campaign、稼働状況、最近の注文コンテキストを返します。",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "support-agent"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"message\",\"meaning\":\"このターンの自然言語の support 問い合わせ。\"}],\"optionalInputs\":[{\"field\":\"sessionId\",\"meaning\":\"support chat を継続するための会話セッション ID。\"}],\"implicitContext\":[{\"field\":\"customerId\",\"source\":\"JWT authentication\",\"meaning\":\"agent prompt に注入される認証済み customer コンテキスト。\"}]}", parseValue = true)
            }))
    public SupportChatResponse chat(@RequestBody SupportChatRequest request) {
        return applicationService.chat(request);
    }

    @PostMapping(path = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Record support feedback",
            description = "注文後の support メッセージを受け取り、分類結果と escalation 方針を返します。",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "support-agent"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"message\",\"meaning\":\"分類対象となる自由記述の feedback または support メッセージ。\"}],\"optionalInputs\":[{\"field\":\"orderId\",\"meaning\":\"feedback が特定注文に関する場合の関連 orderId。\"},{\"field\":\"rating\",\"meaning\":\"customer が任意で付与する数値 rating。\"}]}", parseValue = true)
            }))
    public SupportFeedbackResponse feedback(@RequestBody SupportFeedbackRequest request) {
        return applicationService.feedback(request);
    }

    @GetMapping("/campaigns")
    @Operation(summary = "List active campaigns", description = "support-service が提示可能な現在有効な campaign を返します。")
    public List<CampaignSummary> campaigns() {
        return applicationService.campaigns();
    }

    @GetMapping("/status")
    @Operation(summary = "Read current service status", description = "registry-service を通じて取得した現在の集約 service-status view を返します。")
    public SupportStatusResponse status() {
        return applicationService.status();
    }

    @GetMapping("/execution-history/{sessionId}")
    @Operation(summary = "Read support execution history", description = "指定した session ID に紐づく support-service の実行履歴を返します。")
    public SupportExecutionHistoryResponse executionHistory(@PathVariable String sessionId) {
        return historyStore.history(sessionId);
    }
}