package com.mahitotsu.arachne.samples.delivery.kitchenservice.api;

import static com.mahitotsu.arachne.samples.delivery.kitchenservice.domain.KitchenTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.kitchenservice.application.KitchenApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/internal/kitchen", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Kitchen Service", description = "在庫確認と代替可否判定を行う kitchen-service のエンドポイントです。")
public class KitchenController {

    private final KitchenApplicationService applicationService;

    KitchenController(KitchenApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/check")
    @Operation(
            summary = "Check kitchen availability",
            description = "選択済み item id と元の customer 意図を受け取り、在庫可否、調理時間、代替協調 trace を返します。",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "kitchen-agent"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"itemIds\",\"meaning\":\"在庫可否と調理時間を検証する対象の menu item id。\"},{\"field\":\"message\",\"meaning\":\"代替候補や優先度判定に使う元の customer 意図。\"}],\"optionalInputs\":[{\"field\":\"sessionId\",\"meaning\":\"親ワークフローの相関 ID。\"}]}", parseValue = true)
            }))
    KitchenCheckResponse check(@RequestBody KitchenCheckRequest request) {
        return applicationService.check(request);
    }
}