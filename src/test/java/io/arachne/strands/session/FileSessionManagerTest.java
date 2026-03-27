package io.arachne.strands.session;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.arachne.strands.types.Message;

class FileSessionManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTripsSession() {
        FileSessionManager manager = new FileSessionManager(tempDir);
        AgentSession session = new AgentSession(
                List.of(Message.user("hello"), Message.assistant("world")),
                Map.of("city", "Tokyo"),
                Map.of("type", "SlidingWindowConversationManager", "windowSize", 4, "removedMessageCount", 2));

        manager.save("trip planner", session);

        AgentSession restored = manager.load("trip planner");

        assertThat(restored).isNotNull();
        assertThat(restored.messages()).hasSize(2);
        assertThat(restored.state()).containsEntry("city", "Tokyo");
        assertThat(restored.conversationManagerState()).containsEntry("windowSize", 4);
        assertThat(manager.getStorageDirectory().resolve("trip+planner.json")).exists();
    }

    @Test
    void loadReturnsNullWhenSessionFileDoesNotExist() {
        FileSessionManager manager = new FileSessionManager(tempDir);

        assertThat(manager.load("missing-session")).isNull();
    }

    @Test
    void loadRejectsInvalidJsonSessionFile() throws Exception {
        FileSessionManager manager = new FileSessionManager(tempDir);
        Files.writeString(tempDir.resolve("broken.json"), "{not-json}");

        assertThatThrownBy(() -> manager.load("broken"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to read session file");
    }

    @Test
    void constructorRejectsStoragePathThatIsAFile() throws Exception {
        Path storageFile = Files.writeString(tempDir.resolve("sessions-file"), "content");

        assertThatThrownBy(() -> new FileSessionManager(storageFile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to create session storage directory");
    }
}