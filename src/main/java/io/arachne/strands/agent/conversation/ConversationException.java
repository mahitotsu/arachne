package io.arachne.strands.agent.conversation;

/**
 * Base runtime exception for conversation management failures.
 */
public class ConversationException extends RuntimeException {

    public ConversationException(String message) {
        super(message);
    }

    public ConversationException(String message, Throwable cause) {
        super(message, cause);
    }
}