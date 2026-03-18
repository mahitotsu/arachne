package io.arachne.strands.model.bedrock;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import io.arachne.strands.model.ToolSelection;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;

class BedrockModelRequestTest {

    @Test
    void buildRequestIncludesSystemPrompt() {
        BedrockModel model = new BedrockModel("test-model", "us-west-2");

        var request = model.buildRequest(List.of(Message.user("hello")), List.of(), "Be concise.", null);

        assertThat(request.system()).hasSize(1);
        assertThat(request.system().getFirst().text()).isEqualTo("Be concise.");
    }

    @Test
    void structuredToolResultIsSentAsJson() {
        BedrockModel model = new BedrockModel("test-model", "us-west-2");
        Message message = new Message(
                Message.Role.USER,
                List.of(new ContentBlock.ToolResult("tool-1", Map.of("city", "Tokyo"), "success")));

        var request = model.buildRequest(
                List.of(message),
                List.of(new ToolSpec("weather", "Weather lookup", null)),
                null,
                null);

        ToolResultContentBlock content = request.messages().getFirst().content().getFirst().toolResult().content().getFirst();
        assertThat(content.type()).isEqualTo(ToolResultContentBlock.Type.JSON);
        assertThat(content.json().asMap()).containsKey("city");
    }

    @Test
    void buildRequestCanForceSpecificToolChoice() {
        BedrockModel model = new BedrockModel("test-model", "us-west-2");

        var request = model.buildRequest(
                List.of(Message.user("hello")),
                List.of(new ToolSpec("structured_output", "Final schema", null)),
                null,
                ToolSelection.force("structured_output"));

        assertThat(request.toolConfig().toolChoice().tool().name()).isEqualTo("structured_output");
    }
}