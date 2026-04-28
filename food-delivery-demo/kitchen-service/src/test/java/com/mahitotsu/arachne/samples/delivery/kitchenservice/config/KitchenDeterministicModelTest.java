package com.mahitotsu.arachne.samples.delivery.kitchenservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

class KitchenDeterministicModelTest {

    @Test
    void emitsSubstitutionLookupWithoutUnusedCustomerMessage() {
        KitchenDeterministicModel model = new KitchenDeterministicModel();

        ModelEvent firstEvent = model.converse(
                List.of(
                        Message.user("""
                                items=combo-kids,drink-lemon
                                message=子ども向けにしたい
                                """),
                        new Message(
                                Message.Role.ASSISTANT,
                                List.of(new ContentBlock.ToolResult(
                                        "kitchen-lookup",
                                        Map.of(
                                                "inventorySummary", "combo-kids は欠品です",
                                                "unavailableItemIds", List.of("combo-kids")),
                                        "success"))),
                        new Message(
                                Message.Role.ASSISTANT,
                                List.of(new ContentBlock.ToolResult(
                                        "prep-scheduler",
                                        Map.of(
                                                "scheduleSummary", "12分で用意できます",
                                                "readyInMinutes", 12),
                                        "success")))),
                List.of())
                .iterator()
                .next();

        assertThat(firstEvent).isInstanceOf(ModelEvent.ToolUse.class);
        ModelEvent.ToolUse toolUse = (ModelEvent.ToolUse) firstEvent;
        assertThat(toolUse.name()).isEqualTo("menu_substitution_lookup");
        assertThat(toolUse.input()).isEqualTo(Map.of("unavailableItemIds", List.of("combo-kids")));
    }
}