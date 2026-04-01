package com.mahitotsu.arachne.strands.tool.builtin;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class BuiltInResourceSupportTest {

    @Test
    void payloadUsesUtf8TextForPlainTextContent() {
        Resource resource = namedResource("notes.txt", "hello\n".getBytes(StandardCharsets.UTF_8));

        var payload = BuiltInResourceSupport.payload("resource", "file:/tmp/notes.txt", resource, "hello\n".getBytes(StandardCharsets.UTF_8));

        assertThat(payload)
                .containsEntry("mediaType", "text/plain")
                .containsEntry("encoding", "utf-8")
                .containsEntry("content", "hello\n");
    }

    @Test
    void payloadUsesBase64ForBinaryContent() {
        byte[] bytes = new byte[]{0x00, 0x01, 0x02};
        Resource resource = namedResource("blob.bin", bytes);

        var payload = BuiltInResourceSupport.payload("resource", "file:/tmp/blob.bin", resource, bytes);

        assertThat(payload)
                .containsEntry("mediaType", "application/octet-stream")
                .containsEntry("encoding", "base64")
                .containsEntry("content", Base64.getEncoder().encodeToString(bytes));
    }

    @Test
    void mediaTypeFallsBackToLocationWhenFilenameIsMissing() {
        Resource resource = new ByteArrayResource(new byte[0]);

        assertThat(BuiltInResourceSupport.mediaType(resource, "file:/tmp/app.yaml")).isEqualTo("application/yaml");
    }

    private static Resource namedResource(String filename, byte[] bytes) {
        return new ByteArrayResource(Objects.requireNonNull(bytes)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}