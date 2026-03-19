package io.arachne.strands.agent.conversation;

/**
 * Raised when a conversation history cannot be reduced to a valid window.
 */
public class ContextWindowOverflowException extends ConversationException {

    public ContextWindowOverflowException(String message) {
        super(message);
    }

    public ContextWindowOverflowException(String message, Throwable cause) {
        super(message, cause);
    }
}