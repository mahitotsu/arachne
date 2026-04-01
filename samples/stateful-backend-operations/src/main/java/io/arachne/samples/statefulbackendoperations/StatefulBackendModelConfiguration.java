package com.mahitotsu.arachne.samples.statefulbackendoperations;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.types.Message;

@Configuration(proxyBeanMethods = false)
public class StatefulBackendModelConfiguration {

    @Bean
    Model statefulBackendDemoModel() {
        return new Model() {
            private int step = 0;

            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<io.arachne.strands.model.ToolSpec> tools) {
                step++;
                return switch (step) {
                    case 1 -> List.of(
                            new ModelEvent.ToolUse(
                                    "tool-1",
                                    "prepare_account_update",
                                    Map.of(
                                            "operationKey", "unlock-acct-007",
                                            "accountId", "acct-007",
                                            "targetStatus", "UNLOCKED",
                                            "reason", "manual unlock approved by backend policy")),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(3, 3)));
                    case 2 -> List.of(
                            new ModelEvent.ToolUse(
                                    "tool-2",
                                    "execute_account_update",
                                    Map.of("operationKey", "unlock-acct-007")),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(2, 2)));
                    case 3 -> List.of(
                            new ModelEvent.ToolUse(
                                    "tool-3",
                                    "execute_account_update",
                                    Map.of("operationKey", "unlock-acct-007")),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(2, 2)));
                    case 4 -> List.of(
                            new ModelEvent.ToolUse(
                                    "tool-4",
                                    "get_operation_status",
                                    Map.of("operationKey", "unlock-acct-007")),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(2, 2)));
                    default -> List.of(
                            new ModelEvent.TextDelta("account update prepared, executed, replay-checked, and verified"),
                            new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
                };
            }
        };
    }
}