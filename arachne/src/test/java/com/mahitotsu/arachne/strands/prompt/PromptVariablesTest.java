package com.mahitotsu.arachne.strands.prompt;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import org.junit.jupiter.api.Test;

class PromptVariablesTest {

    @Test
    void emptyVariablesContainsNoKeys() {
        PromptVariables vars = PromptVariables.empty();
        assertThat(vars.contains("anything")).isFalse();
    }

    @Test
    void ofCreatesVariablesFromKeyValuePairs() {
        PromptVariables vars = PromptVariables.of("name", "Alice", "role", "assistant");
        assertThat(vars.contains("name")).isTrue();
        assertThat(vars.get("name")).isEqualTo("Alice");
        assertThat(vars.contains("role")).isTrue();
        assertThat(vars.get("role")).isEqualTo("assistant");
    }

    @Test
    void fromCreatesVariablesFromMap() {
        PromptVariables vars = PromptVariables.from(Map.of("key", "value"));
        assertThat(vars.get("key")).isEqualTo("value");
    }

    @Test
    void ofRejectsOddNumberOfArguments() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PromptVariables.of("a", "b", "c"))
                .withMessageContaining("even number of arguments");
    }

    @Test
    void ofRejectsNullKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PromptVariables.of(null, "value"))
                .withMessageContaining("must not be null");
    }

    @Test
    void ofRejectsNullValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PromptVariables.of("key", null))
                .withMessageContaining("must not be null");
    }

    @Test
    void getMissingKeyReturnsNull() {
        PromptVariables vars = PromptVariables.of("a", "1");
        assertThat(vars.get("b")).isNull();
        assertThat(vars.contains("b")).isFalse();
    }
}
