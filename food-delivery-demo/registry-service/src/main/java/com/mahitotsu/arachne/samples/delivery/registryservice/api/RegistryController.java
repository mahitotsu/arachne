package com.mahitotsu.arachne.samples.delivery.registryservice.api;

import static com.mahitotsu.arachne.samples.delivery.registryservice.domain.RegistryTypes.*;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.registryservice.application.RegistryApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/registry", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Registry Service", description = "capability 登録、discovery、ヘルス集約を行う registry-service のエンドポイントです。")
public class RegistryController {

    private final RegistryApplicationService applicationService;

    RegistryController(RegistryApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a service descriptor", description = "service capability、endpoint、agent、health metadata を registry-service に登録します。")
    RegistryServiceDescriptor register(@RequestBody RegistryRegistration request) {
        return applicationService.register(request);
    }

    @PostMapping("/discover")
    @Operation(
            summary = "Discover registered services",
            description = "自然言語の capability 問い合わせを受け取り、一致する service descriptor と registry agent 要約を返します。",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "capability-registry-agent"),
                @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"query\",\"meaning\":\"capability や service 種別を説明する自然言語の discovery 要求。\"}],\"optionalInputs\":[{\"field\":\"availableOnly\",\"meaning\":\"結果を現在利用可能な service のみに絞り込むかどうか。\"}]}", parseValue = true)
            }))
    RegistryDiscoverResponse discover(@RequestBody RegistryDiscoverRequest request) {
        return applicationService.discover(request);
    }

    @GetMapping("/services")
    @Operation(summary = "List registered services", description = "discovery 可視性のために保持している inactive adapter を含む現在の service descriptor 一覧を返します。")
    List<RegistryServiceDescriptor> services() {
        return applicationService.services();
    }

    @GetMapping("/health")
    @Operation(summary = "Read aggregated service health", description = "登録済み service 全体の現在の health view を返します。")
    RegistryHealthResponse health() {
        return applicationService.health();
    }
}