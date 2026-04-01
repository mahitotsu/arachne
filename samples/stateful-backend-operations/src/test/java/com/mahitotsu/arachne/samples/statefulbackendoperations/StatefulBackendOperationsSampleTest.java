package com.mahitotsu.arachne.samples.statefulbackendoperations;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
class StatefulBackendOperationsSampleTest {

    @Test
    void sampleBootsAndPrintsExpectedIdempotentBackendFlow(CapturedOutput output) {
        assertThat(output)
                .contains("Arachne stateful backend operations sample")
                .contains("request> Unlock account acct-007 with operation key unlock-acct-007.")
                .contains("final.reply> account update prepared, executed, replay-checked, and verified")
                .contains("state.operationKey> unlock-acct-007")
                .contains("state.lastExecutionOutcome> replayed")
                .contains("state.toolTrace>")
            .contains("tool-1:prepare:PREPARED")
            .contains("tool-2:execute:applied:replayed=false")
            .contains("tool-3:execute:replayed:replayed=true")
            .contains("tool-4:status:COMPLETED")
                .contains("db.accountStatus> UNLOCKED")
                .contains("db.operationRecord> OperationRecord[")
                .contains("operationKey=unlock-acct-007")
                .contains("executionState=COMPLETED")
                .contains("finalStatus=UNLOCKED");
    }
}