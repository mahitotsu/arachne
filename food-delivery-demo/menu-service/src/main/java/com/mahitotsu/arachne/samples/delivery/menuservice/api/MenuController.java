package com.mahitotsu.arachne.samples.delivery.menuservice.api;

import static com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.menuservice.application.MenuApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/internal/menu", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Menu Service", description = "提案と代替候補を返す menu-service のエンドポイントです。")
public class MenuController {

    private final MenuApplicationService applicationService;

    MenuController(MenuApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/suggest")
    @Operation(
            summary = "Suggest menu candidates",
            description = "現在の customer の注文意図を受け取り、agent 要約と ETA を伴う menu 候補を返します。",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "menu-agent"),
                @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"query\",\"meaning\":\"好み、人数、予算、欲しい商品を含む主たる注文意図。\"}],\"optionalInputs\":[{\"field\":\"refinement\",\"meaning\":\"再提案のための追加制約。\"},{\"field\":\"recentOrderSummary\",\"meaning\":\"利用可能な場合に呼び出し元が注入する再注文コンテキスト。\"},{\"field\":\"sessionId\",\"meaning\":\"提案ワークフローの相関 ID。\"}]}", parseValue = true)
            }))
    MenuSuggestionResponse suggest(@RequestBody MenuSuggestionRequest request) {
        return applicationService.suggest(request);
    }

    @PostMapping("/substitutes")
    @Operation(
            summary = "Suggest substitute menu candidates",
            description = "欠品商品と元の customer 意図を受け取り、menu-service から代替候補を返します。",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "menu-agent"),
                @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"unavailableItemId\",\"meaning\":\"欠品になった元の menu item id。\"},{\"field\":\"message\",\"meaning\":\"代替候補でも保持したい元の customer 意図。\"}],\"optionalInputs\":[{\"field\":\"sessionId\",\"meaning\":\"代替ワークフローの相関 ID。\"}]}", parseValue = true)
            }))
    MenuSubstitutionResponse substitutes(@RequestBody MenuSubstitutionRequest request) {
        return applicationService.suggestSubstitutes(request);
    }
}