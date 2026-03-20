package io.arachne.samples.phase4hooksinterrupts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;

@SpringBootApplication
public class Phase4HooksInterruptsApplication {

    public static void main(String[] args) {
        SpringApplication.run(Phase4HooksInterruptsApplication.class, args);
    }

    @Bean
    @SuppressWarnings("unused")
    Model demoModel() {
        return new ApprovalWorkflowModel();
    }

    private static final class ApprovalWorkflowModel implements Model {

        private int calls;

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            calls++;
            if (calls == 1) {
                Map<String, Object> input = new LinkedHashMap<>();
                input.put("destination", "Kyoto");
                input.put("nights", 2);
                return List.of(
                        new ModelEvent.ToolUse("approval-tool-1", "approvalTool", input),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(2, 1)));
            }

            ContentBlock.ToolResult toolResult = lastToolResult(messages);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) toolResult.content();
            Object approved = response.getOrDefault("approved", "unknown");
            Object operator = response.getOrDefault("operator", "unknown");

            return List.of(
                    new ModelEvent.TextDelta("Approval recorded for Kyoto: approved=" + approved + " by " + operator),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(3, 2)));
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            return converse(messages, tools, null);
        }

        private ContentBlock.ToolResult lastToolResult(List<Message> messages) {
            Message lastMessage = messages.getLast();
            return lastMessage.content().stream()
                    .filter(ContentBlock.ToolResult.class::isInstance)
                    .map(ContentBlock.ToolResult.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Expected a tool result in the last message."));
        }
    }
}