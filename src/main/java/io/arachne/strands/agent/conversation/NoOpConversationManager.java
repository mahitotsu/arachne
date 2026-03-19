package io.arachne.strands.agent.conversation;

import java.util.List;

import io.arachne.strands.types.Message;

/**
 * Conversation manager that leaves history unchanged.
 */
public class NoOpConversationManager implements ConversationManager {

    @Override
    public void applyManagement(List<Message> messages) {
    }
}