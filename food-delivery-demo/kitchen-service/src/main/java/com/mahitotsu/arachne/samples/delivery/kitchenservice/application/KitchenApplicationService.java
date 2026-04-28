package com.mahitotsu.arachne.samples.delivery.kitchenservice.application;

import static com.mahitotsu.arachne.samples.delivery.kitchenservice.domain.KitchenTypes.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mahitotsu.arachne.samples.delivery.kitchenservice.config.SecurityAccessors;
import com.mahitotsu.arachne.samples.delivery.kitchenservice.infrastructure.KitchenRepository;
import com.mahitotsu.arachne.samples.delivery.kitchenservice.infrastructure.MenuSubstitutionGateway;
import com.mahitotsu.arachne.strands.agent.AgentResult;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.ToolResult;

@Service
public class KitchenApplicationService {

    private final AgentFactory agentFactory;
    private final KitchenRepository repository;
    private final Tool kitchenLookupTool;
    private final Tool prepSchedulerTool;
    private final MenuSubstitutionGateway menuSubstitutionGateway;

    KitchenApplicationService(
            AgentFactory agentFactory,
            KitchenRepository repository,
            @Qualifier("kitchenLookupTool") Tool kitchenLookupTool,
            @Qualifier("prepSchedulerTool") Tool prepSchedulerTool,
            MenuSubstitutionGateway menuSubstitutionGateway) {
        this.agentFactory = agentFactory;
        this.repository = repository;
        this.kitchenLookupTool = kitchenLookupTool;
        this.prepSchedulerTool = prepSchedulerTool;
        this.menuSubstitutionGateway = menuSubstitutionGateway;
    }

    public KitchenCheckResponse check(KitchenCheckRequest request) {
        String accessToken = SecurityAccessors.requiredAccessToken();
        List<KitchenItemStatus> statuses = repository.check(request.itemIds());
        Map<String, MenuSubstitutionResponse> substitutionResponses = fetchSubstitutionResponses(request, accessToken, statuses);
        AgentResult decisionResult = agentFactory.builder()
                .systemPrompt("""
                あなたはこのアプリ唯一のクラウドキッチンの kitchen-agent です。

                代替の支店も代替のキッチンも存在しません。
                キッチンがアイテムを提供できない場合は、他のキッチンではなく欠品時の代替候補提示 capability を持つ協業先に同ブランドの代替品を尋ねてください。

                まず kitchen_inventory_lookup を呼び出して在庫と調理時間を確認してください。
                続けて prep_scheduler を呼び、ライン別のキュー遅延と提供見込み時間を確認してください。
                リクエストされたアイテムが在庫切れの場合は、menu_substitution_lookup を呼び出して代替候補提示 capability を持つ協業先に候補を提案させてください。
                自分のキッチンラインで実際に対応できる代替品のみを承認してください。
                grill-line の遅延が 15 分を超えるときは、assembly 系（例: サーモン丼）に切り替えると早く出せることを能動的に伝えてください。
                最終回答は structured_output を使い、summary と approvedSubstitutions を返してください。
                """)
                .tools(kitchenLookupTool, prepSchedulerTool,
                    buildMenuSubstitutionTool(request, substitutionResponses))
                .build()
                .run("items=" + String.join(",", request.itemIds()) + "\nmessage=" + request.message(), KitchenDecision.class);
        KitchenDecision decision = decisionResult.structuredOutput(KitchenDecision.class);
        Map<String, KitchenItemStatus> approvedSubstitutions = approveSubstitutions(decision.approvedSubstitutions(), substitutionResponses);
        List<KitchenItemStatus> resolvedStatuses = applySubstitutions(statuses, approvedSubstitutions);
        PrepSchedule schedule = repository.schedule(resolvedStatuses.stream()
                .map(status -> status.substituteItemId() != null ? status.substituteItemId() : status.itemId())
                .toList());
        return new KitchenCheckResponse(
                "kitchen-service",
                "kitchen-agent",
                repository.headline(resolvedStatuses),
                decision.summary(),
                schedule.readyInMinutes(),
                resolvedStatuses,
                collaborations(substitutionResponses));
    }

    private Map<String, MenuSubstitutionResponse> fetchSubstitutionResponses(
            KitchenCheckRequest request,
            String accessToken,
            List<KitchenItemStatus> statuses) {
        LinkedHashMap<String, MenuSubstitutionResponse> responses = new LinkedHashMap<>();
        for (KitchenItemStatus status : statuses) {
            if (status.available()) {
                continue;
            }
            responses.put(status.itemId(), menuSubstitutionGateway.suggestSubstitutes(
                    new MenuSubstitutionRequest(request.sessionId(), request.message(), status.itemId()),
                    accessToken));
        }
        return Map.copyOf(responses);
    }

    private Tool buildMenuSubstitutionTool(
            KitchenCheckRequest request,
                Map<String, MenuSubstitutionResponse> substitutionResponses) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "menu_substitution_lookup",
                        "利用不可なリクエストに対して代替候補提示 capability を持つ協業先へ代替品を問い合わせ、その後 kitchen-agent が承認する。",
                        substitutionSchema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("menu_substitution_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> inputValues = inputValues(input);
                List<String> unavailableItemIds = stringList(inputValues.get("unavailableItemIds"));
                ArrayList<Map<String, Object>> candidates = new ArrayList<>();

                for (String unavailableItemId : unavailableItemIds) {
                    MenuSubstitutionResponse response = substitutionResponses.get(unavailableItemId);
                    if (response == null) {
                        continue;
                    }
                    candidates.add(Map.of(
                            "unavailableItemId", unavailableItemId,
                            "summary", response.summary(),
                            "items", response.items().stream().map(item -> Map.of(
                                    "itemId", item.id(),
                                    "name", item.name(),
                                    "category", item.category())).toList()));
                }
                return ToolResult.success(context.toolUseId(), Map.of(
                        "candidates", candidates,
                        "substitutionSummary", candidates.isEmpty()
                                ? "承認できる代替候補はありません"
                                : "menu-agent から代替候補が届いています"));
            }
        };
    }

    private static ObjectNode substitutionSchema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ObjectNode unavailableItemIds = properties.putObject("unavailableItemIds");
        unavailableItemIds.put("type", "array");
        unavailableItemIds.putObject("items").put("type", "string");
        properties.putObject("customerMessage").put("type", "string");
        root.putArray("required").add("unavailableItemIds").add("customerMessage");
        root.put("additionalProperties", false);
        return root;
    }

    private static Map<String, Object> inputValues(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private List<KitchenItemStatus> applySubstitutions(
            List<KitchenItemStatus> statuses,
            Map<String, KitchenItemStatus> approvedSubstitutions) {
        return statuses.stream()
                .map(status -> approvedSubstitutions.getOrDefault(status.itemId(), status))
                .toList();
    }

    private Map<String, KitchenItemStatus> approveSubstitutions(
            List<ApprovedSubstitutionDecision> decisions,
            Map<String, MenuSubstitutionResponse> substitutionResponses) {
        if (decisions == null || decisions.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, KitchenItemStatus> approved = new LinkedHashMap<>();
        for (ApprovedSubstitutionDecision decision : decisions) {
            if (decision == null || decision.unavailableItemId() == null || decision.selectedItemId() == null) {
                continue;
            }
            MenuSubstitutionResponse response = substitutionResponses.get(decision.unavailableItemId());
            if (response == null) {
                continue;
            }
            response.items().stream()
                    .filter(item -> item.id().equals(decision.selectedItemId()))
                    .findFirst()
                    .flatMap(item -> approveSubstitute(decision.unavailableItemId(), List.of(item)))
                    .ifPresent(status -> approved.put(decision.unavailableItemId(), status));
        }
        return Map.copyOf(approved);
    }

    private List<AgentCollaboration> collaborations(Map<String, MenuSubstitutionResponse> substitutionResponses) {
        return substitutionResponses.values().stream()
                .map(response -> new AgentCollaboration("menu-service/support", response.agent(), response.headline(), response.summary()))
                .toList();
    }

    private java.util.Optional<KitchenItemStatus> approveSubstitute(String unavailableItemId, List<MenuItem> candidates) {
        for (MenuItem candidate : candidates) {
            KitchenItemStatus availability = repository.check(List.of(candidate.id())).getFirst();
            if (availability.available()) {
                return java.util.Optional.of(new KitchenItemStatus(
                        unavailableItemId,
                        false,
                        availability.prepMinutes(),
                        candidate.id(),
                        candidate.name(),
                        candidate.price()));
            }
        }
        return java.util.Optional.empty();
    }
}