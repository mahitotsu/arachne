package io.arachne.strands.prompt;

import io.arachne.strands.types.Message;

/**
 * Static factory helpers for building {@link Message} instances from prompt text or templates.
 *
 * <p>{@code MessageBuilder} bridges {@link PromptTemplate} rendering and {@link Message} creation
 * so callers do not need to call {@link Message#user(String)} or {@link Message#assistant(String)}
 * manually after rendering.
 *
 * <pre>{@code
 * // From a template and variables
 * PromptTemplate template = PromptTemplate.of("Summarize the following: {{content}}");
 * Message msg = MessageBuilder.user(template, PromptVariables.of("content", articleText));
 *
 * // From plain text (no template substitution needed)
 * Message msg2 = MessageBuilder.user("What is the capital of France?");
 * }</pre>
 */
public final class MessageBuilder {

    private MessageBuilder() {}

    // --- User messages ---

    /**
     * Creates a {@link Message.Role#USER user} message from a plain text string.
     *
     * @param text the message text; must not be {@code null}
     * @return a user {@link Message}
     */
    public static Message user(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Message text must not be null");
        }
        return Message.user(text);
    }

    /**
     * Creates a {@link Message.Role#USER user} message by rendering {@code template} with
     * {@code variables}.
     *
     * @param template  the prompt template; must not be {@code null}
     * @param variables the variable map; must not be {@code null}
     * @return a user {@link Message} with the rendered text as its content
     * @throws IllegalArgumentException if any template variable is missing
     */
    public static Message user(PromptTemplate template, PromptVariables variables) {
        return Message.user(template.render(variables));
    }

    // --- Assistant messages ---

    /**
     * Creates a {@link Message.Role#ASSISTANT assistant} message from a plain text string.
     *
     * @param text the message text; must not be {@code null}
     * @return an assistant {@link Message}
     */
    public static Message assistant(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Message text must not be null");
        }
        return Message.assistant(text);
    }

    /**
     * Creates a {@link Message.Role#ASSISTANT assistant} message by rendering {@code template}
     * with {@code variables}.
     *
     * @param template  the prompt template; must not be {@code null}
     * @param variables the variable map; must not be {@code null}
     * @return an assistant {@link Message} with the rendered text as its content
     * @throws IllegalArgumentException if any template variable is missing
     */
    public static Message assistant(PromptTemplate template, PromptVariables variables) {
        return Message.assistant(template.render(variables));
    }
}
