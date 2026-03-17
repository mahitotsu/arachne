package io.arachne.strands.agent;

import io.arachne.strands.types.Message;

import java.util.List;

/**
 * Result returned from {@link Agent#run(String)}.
 */
public record AgentResult(
        String text,
        List<Message> messages,
        Object stopReason
) {}
