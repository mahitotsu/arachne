package com.mahitotsu.arachne.samples.streamingsteering;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
class StreamingSteeringSampleTest {

    @Autowired
    private DemoStreamingSteeringModel demoStreamingSteeringModel;

    @Test
    void sampleBootsAndPrintsExpectedStreamingAndSteeringFlow(CapturedOutput output) {
        assertThat(output)
                .contains("Arachne streaming and steering sample")
                .contains("request> Can I refund an unopened item?")
                .contains("stream.1> text=Checking the refund guidance...")
                .contains("stream.2> toolUse=policy_lookup {topic=refunds}")
                .contains("stream.3> toolResult=error Use the cached refund policy summary instead of the live lookup.")
                .contains("stream.5> retry=Provide the cached refund policy summary directly.")
                .contains("stream.7> complete=end_turn")
                .contains("final.stopReason> end_turn")
                .contains("tool.invocations> 0")
                .contains("conversation.guidancePresent> true")
                .contains("model.invocations> 3");

        assertThat(demoStreamingSteeringModel.invocationCount()).isEqualTo(3);
    }
}