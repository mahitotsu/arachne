package com.mahitotsu.arachne.strands.tool.builtin;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.strands.tool.ToolResult;

class CalculatorToolTest {

    private final CalculatorTool tool = new CalculatorTool();

    @Test
    void evaluatesArithmeticExpressions() {
        Object content = tool.invoke(Map.of("expression", "1 + 2 * (3 + 4)")).content();

        assertThat(content)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("type", "calculator")
                .containsEntry("expression", "1 + 2 * (3 + 4)")
                .containsEntry("result", "15");
    }

    @Test
    void supportsHelperFunctions() {
        Object content = tool.invoke(Map.of("expression", "max(abs(-2.5), round(2.345, 2), min(4, 1 + 1, 3))")).content();

        assertThat(content)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("result", "2.5");
    }

    @Test
    void formatsWholeNumbersWithoutTrailingZeros() {
        Object content = tool.invoke(Map.of("expression", "1.200 + 0.030")).content();

        assertThat(content)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("result", "1.23");
    }

    @Test
    void rejectsInvalidExpressions() {
        ToolResult result = tool.invoke(Map.of("expression", "1 + )"));

        assertThat(result.status()).isEqualTo(ToolResult.ToolStatus.ERROR);
        assertThat(result.content()).isEqualTo("Unexpected token ')'. At position 4.");
    }

    @Test
    void rejectsDivideByZero() {
        ToolResult result = tool.invoke(Map.of("expression", "1 / 0"));

        assertThat(result.status()).isEqualTo(ToolResult.ToolStatus.ERROR);
        assertThat(result.content()).isEqualTo("Division by zero is not allowed. At position 5.");
    }
}