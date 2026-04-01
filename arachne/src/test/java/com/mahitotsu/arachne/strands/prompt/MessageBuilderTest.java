package com.mahitotsu.arachne.strands.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

class MessageBuilderTest {

    // --- user from plain text ---

    @Test
    void buildsUserMessageFromText() {
        Message msg = MessageBuilder.user("What is Java?");
        assertThat(msg.role()).isEqualTo(Message.Role.USER);
        assertThat(msg.content()).hasSize(1);
        assertThat(msg.content().getFirst()).isInstanceOf(ContentBlock.Text.class);
        assertThat(((ContentBlock.Text) msg.content().getFirst()).text()).isEqualTo("What is Java?");
    }

    @Test
    void buildsUserMessageFromTemplateAndVariables() {
        PromptTemplate template = PromptTemplate.of("Summarize: {{topic}}");
        Message msg = MessageBuilder.user(template, PromptVariables.of("topic", "Spring Boot"));
        assertThat(msg.role()).isEqualTo(Message.Role.USER);
        ContentBlock.Text text = (ContentBlock.Text) msg.content().getFirst();
        assertThat(text.text()).isEqualTo("Summarize: Spring Boot");
    }

    // --- assistant from plain text ---

    @Test
    void buildsAssistantMessageFromText() {
        Message msg = MessageBuilder.assistant("I can help with that.");
        assertThat(msg.role()).isEqualTo(Message.Role.ASSISTANT);
        ContentBlock.Text text = (ContentBlock.Text) msg.content().getFirst();
        assertThat(text.text()).isEqualTo("I can help with that.");
    }

    @Test
    void buildsAssistantMessageFromTemplateAndVariables() {
        PromptTemplate template = PromptTemplate.of("The answer is {{answer}}.");
        Message msg = MessageBuilder.assistant(template, PromptVariables.of("answer", "42"));
        assertThat(msg.role()).isEqualTo(Message.Role.ASSISTANT);
        ContentBlock.Text text = (ContentBlock.Text) msg.content().getFirst();
        assertThat(text.text()).isEqualTo("The answer is 42.");
    }

    // --- delegation to existing Message factories ---

    @Test
    void userFromTextIsBehaviorallyEquivalentToMessageUser() {
        String text = "Hello!";
        Message fromBuilder = MessageBuilder.user(text);
        Message fromFactory = Message.user(text);
        assertThat(fromBuilder.role()).isEqualTo(fromFactory.role());
        assertThat(fromBuilder.content()).isEqualTo(fromFactory.content());
    }

    @Test
    void assistantFromTextIsBehaviorallyEquivalentToMessageAssistant() {
        String text = "Sure!";
        Message fromBuilder = MessageBuilder.assistant(text);
        Message fromFactory = Message.assistant(text);
        assertThat(fromBuilder.role()).isEqualTo(fromFactory.role());
        assertThat(fromBuilder.content()).isEqualTo(fromFactory.content());
    }

    // --- missing variable propagates ---

    @Test
    void userFromTemplateFailsWhenVariableIsMissing() {
        PromptTemplate template = PromptTemplate.of("Hello, {{name}}!");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MessageBuilder.user(template, PromptVariables.empty()))
                .withMessage("Missing template variable: name");
    }

    // --- null guards ---

    @Test
    void rejectsNullTextForUser() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MessageBuilder.user((String) null))
                .withMessage("Message text must not be null");
    }

    @Test
    void rejectsNullTextForAssistant() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MessageBuilder.assistant((String) null))
                .withMessage("Message text must not be null");
    }
}
