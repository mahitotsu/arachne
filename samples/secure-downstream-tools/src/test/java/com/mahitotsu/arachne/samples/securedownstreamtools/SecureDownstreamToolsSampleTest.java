package com.mahitotsu.arachne.samples.securedownstreamtools;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
class SecureDownstreamToolsSampleTest {

    @Test
    void sampleBootsAndPrintsExpectedSecurityAndDownstreamFlow(CapturedOutput output) {
        assertThat(output)
                .contains("Arachne secure downstream tools sample")
                .contains("request> Check what the current operator can do and fetch customer cust-42.")
                .contains("final.reply> capability view and downstream profile fetched")
                .contains("capabilities.view> CurrentOperatorCapabilities[")
            .contains("operatorId=operator-42")
            .contains("tenantId=tenant-north")
                .contains("profile.summary> CustomerProfileSummary[")
                .contains("customerId=cust-42")
            .contains("displayName=Aiko Tanaka")
                .contains("downstream.requests> [cust-42]")
                .contains("downstream.authHeaders> [Bearer token-demo-42]");
    }
}