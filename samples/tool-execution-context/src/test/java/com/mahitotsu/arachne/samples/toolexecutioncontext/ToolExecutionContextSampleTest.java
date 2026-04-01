package com.mahitotsu.arachne.samples.toolexecutioncontext;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
class ToolExecutionContextSampleTest {

    @Test
    void sampleBootsAndPrintsExpectedInvocationAndPropagationSplit(CapturedOutput output) {
        assertThat(output)
                .contains("Arachne tool execution context sample")
                .contains("request> Demonstrate the tool context split.")
                .contains("final.reply> completed tool demo")
                .contains("state.toolCalls> [tool-1:context_echo:alpha, tool-2:context_echo:beta]")
                .contains("propagation.requestIds> [demo-request-42, demo-request-42]")
                .contains("tool.results> [tool-1|context_echo|alpha|demo-request-42, tool-2|context_echo|beta|demo-request-42]");
    }
}