package io.arachne.strands.agent.conversation;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;

class SummarizingConversationManagerTest {

    @Test
    void applyManagementSummarizesOlderMessagesAndKeepsRecentOnes() {
        RecordingSummaryModel summaryModel = new RecordingSummaryModel(List.of("summary one"));
        SummarizingConversationManager manager = new SummarizingConversationManager(summaryModel, 4, 2);
        List<Message> messages = new ArrayList<>(List.of(
                Message.user("first"),
                Message.assistant("one"),
                Message.user("second"),
                Message.assistant("two"),
                Message.user("third"),
                Message.assistant("three")));

        manager.applyManagement(messages);

        assertThat(messages).hasSize(3);
        assertThat(messages.getFirst().role()).isEqualTo(Message.Role.ASSISTANT);
        assertThat(messages.getFirst().content()).containsExactly(
                ContentBlock.text(SummarizingConversationManager.SUMMARY_MESSAGE_PREFIX + "summary one"));
        assertThat(messages.get(1).content()).containsExactly(ContentBlock.text("third"));
        assertThat(messages.get(2).content()).containsExactly(ContentBlock.text("three"));
        assertThat(manager.getSummary()).isEqualTo("summary one");
        assertThat(manager.getSummarizedMessageCount()).isEqualTo(4);
        assertThat(summaryModel.prompts()).singleElement().asString().contains("USER:");
    }

    @Test
    void applyManagementCarriesForwardExistingSummary() {
        RecordingSummaryModel summaryModel = new RecordingSummaryModel(List.of("summary one", "summary two"));
        SummarizingConversationManager manager = new SummarizingConversationManager(summaryModel, 4, 2);
        List<Message> messages = new ArrayList<>(List.of(
                Message.user("first"),
                Message.assistant("one"),
                Message.user("second"),
                Message.assistant("two"),
                Message.user("third"),
                Message.assistant("three")));

        manager.applyManagement(messages);
        messages.add(Message.user("fourth"));
        messages.add(Message.assistant("four"));

        manager.applyManagement(messages);

        assertThat(messages).hasSize(3);
        assertThat(messages.getFirst().content()).containsExactly(
                ContentBlock.text(SummarizingConversationManager.SUMMARY_MESSAGE_PREFIX + "summary two"));
        assertThat(manager.getSummary()).isEqualTo("summary two");
        assertThat(manager.getSummarizedMessageCount()).isEqualTo(6);
        assertThat(summaryModel.prompts()).hasSize(2);
        assertThat(summaryModel.prompts().get(1)).contains("Existing summary:");
        assertThat(summaryModel.prompts().get(1)).contains("summary one");
    }

    @Test
    void restoreRoundTripsSummaryState() {
        RecordingSummaryModel summaryModel = new RecordingSummaryModel(List.of("summary one"));
        SummarizingConversationManager original = new SummarizingConversationManager(summaryModel, 4, 2);
        List<Message> messages = new ArrayList<>(List.of(
                Message.user("first"),
                Message.assistant("one"),
                Message.user("second"),
                Message.assistant("two"),
                Message.user("third"),
                Message.assistant("three")));

        original.applyManagement(messages);

        SummarizingConversationManager restored = new SummarizingConversationManager(summaryModel, 4, 2);
        restored.restore(original.getState());

        assertThat(restored.getSummary()).isEqualTo("summary one");
        assertThat(restored.getSummarizedMessageCount()).isEqualTo(4);
    }

    @Test
    void rejectsToolUseFromSummaryModel() {
        Model summaryModel = (messages, tools) -> List.of(
                new ModelEvent.ToolUse("summary-tool", "lookup", java.util.Map.of()),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        SummarizingConversationManager manager = new SummarizingConversationManager(summaryModel, 4, 2);
        List<Message> messages = new ArrayList<>(List.of(
                Message.user("first"),
                Message.assistant("one"),
                Message.user("second"),
                Message.assistant("two"),
                Message.user("third")));

        assertThatThrownBy(() -> manager.applyManagement(messages))
                .isInstanceOf(ConversationException.class)
                .hasMessageContaining("tool use");
    }

    private static final class RecordingSummaryModel implements Model {

        private final List<String> summaries;
        private final List<String> prompts = new ArrayList<>();
        private int index;

        private RecordingSummaryModel(List<String> summaries) {
            this.summaries = List.copyOf(summaries);
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<io.arachne.strands.model.ToolSpec> tools) {
            throw new AssertionError("SummarizingConversationManager should use the system-prompt-aware overload");
        }

        @Override
        public Iterable<ModelEvent> converse(
                List<Message> messages,
                List<io.arachne.strands.model.ToolSpec> tools,
                String systemPrompt) {
            prompts.add(((ContentBlock.Text) messages.getFirst().content().getFirst()).text());
            String summary = summaries.get(index++);
            return List.of(
                    new ModelEvent.TextDelta(summary),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        private List<String> prompts() {
            return prompts;
        }
    }
}