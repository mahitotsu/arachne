package io.arachne.strands.session;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON file-backed session storage.
 */
public class FileSessionManager implements SessionManager {

    private final Path storageDirectory;
    private final ObjectMapper objectMapper;

    public FileSessionManager(Path storageDirectory) {
        this(storageDirectory, new ObjectMapper());
    }

    public FileSessionManager(Path storageDirectory, ObjectMapper objectMapper) {
        this.storageDirectory = storageDirectory;
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create session storage directory: " + storageDirectory, e);
        }
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }

    @Override
    public AgentSession load(String sessionId) {
        Path sessionFile = sessionFile(sessionId);
        if (!Files.exists(sessionFile)) {
            return null;
        }
        try {
            return objectMapper.readValue(sessionFile.toFile(), AgentSession.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read session file: " + sessionFile, e);
        }
    }

    @Override
    public void save(String sessionId, AgentSession session) {
        Path sessionFile = sessionFile(sessionId);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(sessionFile.toFile(), session);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write session file: " + sessionFile, e);
        }
    }

    private Path sessionFile(String sessionId) {
        String filename = URLEncoder.encode(sessionId, StandardCharsets.UTF_8) + ".json";
        return storageDirectory.resolve(filename);
    }
}