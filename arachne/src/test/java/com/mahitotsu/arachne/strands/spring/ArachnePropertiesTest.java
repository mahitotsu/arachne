package com.mahitotsu.arachne.strands.spring;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ArachnePropertiesTest {

    @Test
    void defaultsExposeExpectedModelRetryAndBuiltInSettings() {
        ArachneProperties properties = new ArachneProperties();

        assertThat(properties.getModel().getProvider()).isEqualTo("bedrock");
        assertThat(properties.getAgent().getBuiltIns().isInheritDefaults()).isTrue();
        assertThat(properties.getAgent().getBuiltIns().getResources().getAllowedClasspathLocations())
                .containsExactly("classpath:/");
        assertThat(properties.getAgent().getRetry().isEnabled()).isFalse();
        assertThat(properties.getAgent().getRetry().getMaxAttempts()).isEqualTo(6);
        assertThat(properties.getAgent().getRetry().getInitialDelay()).isEqualTo(Duration.ofSeconds(4));
        assertThat(properties.getAgent().getRetry().getMaxDelay()).isEqualTo(Duration.ofSeconds(240));
        assertThat(properties.getModel().getBedrock().getCache().isSystemPrompt()).isFalse();
        assertThat(properties.getModel().getBedrock().getCache().isTools()).isFalse();
    }

    @Test
    void nestedOverrideContainersRemainWritable() {
        ArachneProperties.NamedAgentProperties namedAgent = new ArachneProperties.NamedAgentProperties();

        namedAgent.getModel().setRegion("us-west-2");
        namedAgent.getModel().getBedrock().getCache().setTools(Boolean.TRUE);
        namedAgent.getBuiltIns().setInheritDefaults(Boolean.FALSE);
        namedAgent.getBuiltIns().setToolGroups(List.of("resource"));

        assertThat(namedAgent.getModel().getRegion()).isEqualTo("us-west-2");
        assertThat(namedAgent.getModel().getBedrock().getCache().getTools()).isTrue();
        assertThat(namedAgent.getBuiltIns().getInheritDefaults()).isFalse();
        assertThat(namedAgent.getBuiltIns().getToolGroups()).containsExactly("resource");
    }
}