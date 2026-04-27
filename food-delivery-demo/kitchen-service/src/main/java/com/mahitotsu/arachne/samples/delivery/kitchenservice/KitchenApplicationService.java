package com.mahitotsu.arachne.samples.delivery.kitchenservice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.ToolResult;
import com.mahitotsu.arachne.strands.model.ToolSpec;

@Service
class KitchenApplicationService {

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

    KitchenCheckResponse check(KitchenCheckRequest request) {
        String accessToken = SecurityAccessors.requiredAccessToken();
        List<KitchenItemStatus> statuses = repository.check(request.itemIds());
        AtomicReference<Map<String, KitchenItemStatus>> approvedSubstitutions = new AtomicReference<>(Map.of());
        AtomicReference<List<AgentCollaboration>> collaborations = new AtomicReference<>(List.of());
        String summary = agentFactory.builder()
                .systemPrompt("""
                あなたはこのアプリ唯一のクラウドキッチンの kitchen-agent です。

                代替の支店も代替のキッチンも存在しません。
                キッチンがアイテムを提供できない場合は、他のキッチンではなく欠品時の代替候補提示 capability を持つ協業先に同ブランドの代替品を尋ねてください。

                まず kitchen_inventory_lookup を呼び出して在庫と調理時間を確認してください。
                続けて prep_scheduler を呼び、ライン別のキュー遅延と提供見込み時間を確認してください。
                リクエストされたアイテムが在庫切れの場合は、menu_substitution_lookup を呼び出して代替候補提示 capability を持つ協業先に候補を提案させてください。
                自分のキッチンラインで実際に対応できる代替品のみを承認してください。
                grill-line の遅延が 15 分を超えるときは、assembly 系（例: サーモン丼）に切り替えると早く出せることを能動的に伝えてください。
                最終的な判断を短い段落で説明してください。
                """)
                .tools(kitchenLookupTool, prepSchedulerTool,
                        buildMenuSubstitutionTool(request, accessToken, approvedSubstitutions, collaborations))
                .build()
                .run("items=" + String.join(",", request.itemIds()) + "\nmessage=" + request.message())
                .text();
        List<KitchenItemStatus> resolvedStatuses = applySubstitutions(statuses, approvedSubstitutions.get());
        PrepSchedule schedule = repository.schedule(resolvedStatuses.stream()
                .map(status -> status.substituteItemId() != null ? status.substituteItemId() : status.itemId())
                .toList());
        return new KitchenCheckResponse(
                "kitchen-service",
                "kitchen-agent",
                repository.headline(resolvedStatuses),
                summary,
                schedule.readyInMinutes(),
                resolvedStatuses,
                collaborations.get());
    }

    private Tool buildMenuSubstitutionTool(
            KitchenCheckRequest request,
            String accessToken,
            AtomicReference<Map<String, KitchenItemStatus>> approvedSubstitutions,
            AtomicReference<List<AgentCollaboration>> collaborations) {
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
                String customerMessage = String.valueOf(inputValues.getOrDefault("customerMessage", request.message()));

                LinkedHashMap<String, KitchenItemStatus> approved = new LinkedHashMap<>();
                ArrayList<AgentCollaboration> collaboratorEntries = new ArrayList<>();
                ArrayList<String> approvedNames = new ArrayList<>();

                for (String unavailableItemId : unavailableItemIds) {
                    MenuSubstitutionResponse response = menuSubstitutionGateway.suggestSubstitutes(
                            new MenuSubstitutionRequest(request.sessionId(), customerMessage, unavailableItemId), accessToken);
                    collaboratorEntries.add(new AgentCollaboration(
                            "menu-service/support",
                            response.agent(),
                            response.headline(),
                            response.summary()));
                    approveSubstitute(unavailableItemId, response.items()).ifPresent(status -> {
                        approved.put(unavailableItemId, status);
                        approvedNames.add(status.substituteName());
                    });
                }

                approvedSubstitutions.set(Map.copyOf(approved));
                collaborations.set(List.copyOf(collaboratorEntries));
                return ToolResult.success(context.toolUseId(), Map.of(
                        "substitutionSummary", approvedNames.isEmpty()
                                ? "承認された代替品はありません"
                                : String.join(", ", approvedNames)));
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