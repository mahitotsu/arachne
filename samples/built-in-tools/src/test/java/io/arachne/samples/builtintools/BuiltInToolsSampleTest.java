package io.arachne.samples.builtintools;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
class BuiltInToolsSampleTest {

    @Test
    void sampleBootsAndPrintsExpectedBuiltInToolProfiles(CapturedOutput output) {
        assertThat(output)
                .contains("Arachne built-in tools sample")
                .contains("default.tools> calculator, current_time, resource_reader, resource_list")
                .contains("math.tools> calculator")
                .contains("reader.tools> resource_reader, resource_list")
                .contains("strict.tools> (none)")
                .contains("default.reply> At ")
                .contains(" in Asia/Tokyo I read the sample note: Built-in tools sample note.")
                .contains("default.toolResults> [current_time, resource]")
                .contains("math.reply> Calculator agent computed 1 + 2 * (3 + 4) = 15")
                .contains("math.toolResults> [calculator]")
                .contains("reader.reply> Reader agent found [classpath:/builtin/release-note.md] and read: Built-in tools sample note.")
                .contains("reader.toolResults> [resource_list, resource]");
    }
}