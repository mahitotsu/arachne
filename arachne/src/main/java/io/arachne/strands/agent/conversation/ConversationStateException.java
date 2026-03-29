package io.arachne.strands.agent.conversation;

/**
 * Raised when persisted conversation-manager state is incompatible with the current manager.
 */
public class ConversationStateException extends ConversationException {

    public ConversationStateException(String message) {
        super(message);
    }

    public ConversationStateException(String message, Throwable cause) {
        super(message, cause);
    }
}