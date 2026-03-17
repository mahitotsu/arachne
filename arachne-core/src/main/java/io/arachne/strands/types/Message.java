package io.arachne.strands.types;

import java.util.List;

/**
 * A single turn in the conversation.
 *
 * <p>Corresponds to the Bedrock / OpenAI {@code Message} structure.
 */
public record Message(
        Role role,
        List<ContentBlock> content
) {

    public enum Role {
        USER, ASSISTANT
    }

    public static Message user(String text) {
        return new Message(Role.USER, List.of(ContentBlock.text(text)));
    }

    public static Message assistant(String text) {
        return new Message(Role.ASSISTANT, List.of(ContentBlock.text(text)));
    }
}
