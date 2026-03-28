package io.arachne.strands.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import org.junit.jupiter.api.Test;

class PromptTemplateTest {

    // --- Basic substitution ---

    @Test
    void rendersSinglePlaceholder() {
        PromptTemplate t = PromptTemplate.of("Hello, {{name}}!");
        String result = t.render(PromptVariables.of("name", "Alice"));
        assertThat(result).isEqualTo("Hello, Alice!");
    }

    @Test
    void rendersMultiplePlaceholders() {
        PromptTemplate t = PromptTemplate.of("{{greeting}}, {{name}}. You are a {{role}}.");
        String result = t.render(PromptVariables.of(
                "greeting", "Good morning",
                "name", "Bob",
                "role", "helpful assistant"));
        assertThat(result).isEqualTo("Good morning, Bob. You are a helpful assistant.");
    }

    @Test
    void rendersTemplateWithNoPlaceholders() {
        PromptTemplate t = PromptTemplate.of("No placeholders here.");
        String result = t.render(PromptVariables.empty());
        assertThat(result).isEqualTo("No placeholders here.");
    }

    // --- Repeated placeholders ---

    @Test
    void rendersRepeatedPlaceholderConsistently() {
        PromptTemplate t = PromptTemplate.of("{{x}} + {{x}} = ?");
        String result = t.render(PromptVariables.of("x", "1"));
        assertThat(result).isEqualTo("1 + 1 = ?");
    }

    // --- Extra variables ---

    @Test
    void ignoresExtraVariablesNotUsedInTemplate() {
        PromptTemplate t = PromptTemplate.of("Only {{a}} is used.");
        String result = t.render(PromptVariables.of("a", "this", "b", "ignored"));
        assertThat(result).isEqualTo("Only this is used.");
    }

    // --- Missing variables ---

    @Test
    void failsWithClearMessageWhenVariableIsMissing() {
        PromptTemplate t = PromptTemplate.of("Hello, {{name}}!");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> t.render(PromptVariables.empty()))
                .withMessage("Missing template variable: name");
    }

    @Test
    void failsOnFirstMissingVariableEncountered() {
        PromptTemplate t = PromptTemplate.of("{{a}} and {{b}}");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> t.render(PromptVariables.of("b", "present")))
                .withMessage("Missing template variable: a");
    }

    // --- Placeholder escaping ---

    @Test
    void escapedPlaceholderRendersAsLiteralDoubleBraces() {
        PromptTemplate t = PromptTemplate.of("Use \\{{name}} as a placeholder.");
        String result = t.render(PromptVariables.empty());
        assertThat(result).isEqualTo("Use {{name}} as a placeholder.");
    }

    @Test
    void escapedAndNonEscapedPlaceholdersCoexist() {
        PromptTemplate t = PromptTemplate.of("Rendered: {{value}}. Literal: \\{{value}}.");
        String result = t.render(PromptVariables.of("value", "hello"));
        assertThat(result).isEqualTo("Rendered: hello. Literal: {{value}}.");
    }

    @Test
    void backslashNotFollowedByPlaceholderIsKeptAsIs() {
        PromptTemplate t = PromptTemplate.of("path\\to\\file and {{var}}");
        String result = t.render(PromptVariables.of("var", "end"));
        assertThat(result).isEqualTo("path\\to\\file and end");
    }

    // --- Null guards ---

    @Test
    void rejectsNullTemplate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PromptTemplate.of(null))
                .withMessage("Template text must not be null");
    }

    @Test
    void rejectsNullVariables() {
        PromptTemplate t = PromptTemplate.of("{{x}}");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> t.render(null))
                .withMessage("PromptVariables must not be null");
    }

    // --- Accessor ---

    @Test
    void templateAccessorReturnsRawText() {
        String raw = "Hello, {{name}}!";
        assertThat(PromptTemplate.of(raw).template()).isEqualTo(raw);
    }
}
