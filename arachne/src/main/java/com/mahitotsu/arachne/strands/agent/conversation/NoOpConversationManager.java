package com.mahitotsu.arachne.strands.agent.conversation;

import java.util.List;

import com.mahitotsu.arachne.strands.types.Message;

/**
 * Conversation manager that leaves history unchanged.
 */
public class NoOpConversationManager implements ConversationManager {

    @Override
    public void applyManagement(List<Message> messages) {
    }
}