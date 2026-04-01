package com.mahitotsu.arachne.strands.tool.builtin;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.strands.tool.ToolResult;

class CurrentTimeToolTest {

    private final CurrentTimeTool tool = new CurrentTimeTool();

    @Test
    void returnsStructuredPayloadInRequestedZone() {
        Object content = tool.invoke(Map.of("zoneId", "Asia/Tokyo")).content();

        assertThat(content)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("type", "current_time")
                .containsEntry("zoneId", "Asia/Tokyo")
                .containsKeys("instant", "zonedDateTime", "localDate", "localTime");
    }

    @Test
    void rejectsUnknownZones() {
        ToolResult result = tool.invoke(Map.of("zoneId", "Mars/Olympus"));

        assertThat(result.status()).isEqualTo(ToolResult.ToolStatus.ERROR);
        assertThat(result.content()).isEqualTo("Unknown time zone: Mars/Olympus");
    }
}