package com.mahitotsu.arachne.strands.types;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class MessageTest {

    @Test
    void constructorCopiesContentList() {
        List<ContentBlock> content = new ArrayList<>(List.of(ContentBlock.text("hello")));

        Message message = new Message(Message.Role.USER, content);
        content.add(ContentBlock.text("later"));

        assertThat(message.content()).containsExactly(ContentBlock.text("hello"));
    }
}