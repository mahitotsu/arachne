package io.arachne.strands.agent.conversation;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;

class SlidingWindowConversationManagerTest {

    @Test
    void applyManagementTrimsOldestMessagesToWindowSize() {
        SlidingWindowConversationManager manager = new SlidingWindowConversationManager(2);
        List<Message> messages = new ArrayList<>(List.of(
                Message.user("first"),
                Message.assistant("one"),
                Message.user("second"),
                Message.assistant("two")));

        manager.applyManagement(messages);

        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().content()).containsExactly(ContentBlock.text("second"));
        assertThat(manager.getRemovedMessageCount()).isEqualTo(2);
    }

    @Test
    void applyManagementSkipsDanglingToolResultsAtWindowBoundary() {
        SlidingWindowConversationManager manager = new SlidingWindowConversationManager(2);
        List<Message> messages = new ArrayList<>(List.of(
                Message.user("question"),
                new Message(Message.Role.ASSISTANT, List.of(new ContentBlock.ToolUse("tool-1", "weather", java.util.Map.of()))),
                new Message(Message.Role.USER, List.of(new ContentBlock.ToolResult("tool-1", "sunny", "success"))),
                Message.assistant("final answer")));

        manager.applyManagement(messages);

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().content()).containsExactly(ContentBlock.text("final answer"));
        assertThat(manager.getRemovedMessageCount()).isEqualTo(3);
    }

    @Test
    void rejectsNonPositiveWindowSize() {
        assertThatThrownBy(() -> new SlidingWindowConversationManager(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowSize");
    }

    @Test
    void restoreRejectsMismatchedStateType() {
        SlidingWindowConversationManager manager = new SlidingWindowConversationManager(4);

        assertThatThrownBy(() -> manager.restore(java.util.Map.of("type", "OtherConversationManager")))
                .isInstanceOf(ConversationStateException.class)
                .hasMessageContaining("type mismatch");
    }

    @Test
    void overflowExceptionIsAConversationException() {
        assertThat(new ContextWindowOverflowException("overflow"))
                .isInstanceOf(ConversationException.class)
                .hasMessage("overflow");
    }
}