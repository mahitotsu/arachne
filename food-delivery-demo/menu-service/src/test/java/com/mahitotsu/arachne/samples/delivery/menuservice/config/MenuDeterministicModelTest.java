package com.mahitotsu.arachne.samples.delivery.menuservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure.MenuRepository;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.types.Message;

class MenuDeterministicModelTest {

    @Test
    void emitsMessageFieldForSubstitutionLookup() {
        MenuDeterministicModel model = new MenuDeterministicModel();

        ModelEvent firstEvent = model.converse(
                List.of(Message.user("""
                        unavailableItemId=combo-kids
                        message=子ども向けにしたい
                        """)),
                List.<ToolSpec>of())
                .iterator()
                .next();

        assertThat(firstEvent).isInstanceOf(ModelEvent.ToolUse.class);
        ModelEvent.ToolUse toolUse = (ModelEvent.ToolUse) firstEvent;
        assertThat(toolUse.name()).isEqualTo("menu_substitution_lookup");
        assertThat(toolUse.input()).isEqualTo(Map.of(
                "unavailableItemId", "combo-kids",
                "message", "子ども向けにしたい"));
    }

    @Test
    void substitutionToolSchemaUsesMessageFieldName() {
        ToolSpec spec = new MenuArachneConfiguration()
                .menuSubstitutionLookupTool(new MenuRepository())
                .spec();

        assertThat(spec.inputSchema().toPrettyString())
                .contains("\"unavailableItemId\"")
                .contains("\"message\"")
                .doesNotContain("customerMessage");
    }
}