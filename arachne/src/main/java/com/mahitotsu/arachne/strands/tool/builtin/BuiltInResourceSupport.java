package com.mahitotsu.arachne.strands.tool.builtin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.Resource;

final class BuiltInResourceSupport {

    private BuiltInResourceSupport() {
    }

    static byte[] readBytes(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    static Map<String, Object> payload(String type, String location, Resource resource, byte[] bytes) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("location", location);
        payload.put("mediaType", mediaType(resource, location));
        payload.put("size", bytes.length);
        if (isUtf8Text(bytes)) {
            payload.put("encoding", "utf-8");
            payload.put("content", new String(bytes, StandardCharsets.UTF_8));
        } else {
            payload.put("encoding", "base64");
            payload.put("content", Base64.getEncoder().encodeToString(bytes));
        }
        return Map.copyOf(payload);
    }

    static String mediaType(Resource resource, String location) {
        String fileName = resource.getFilename();
        String lower = (fileName == null ? location : fileName).toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".log")) {
            return "text/plain";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return "application/yaml";
        }
        if (lower.endsWith(".sh")) {
            return "text/x-shellscript";
        }
        return "application/octet-stream";
    }

    private static boolean isUtf8Text(byte[] bytes) {
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            return false;
        }

        for (byte value : bytes) {
            if ((value & 0xFF) == 0) {
                return false;
            }
        }
        return true;
    }
}