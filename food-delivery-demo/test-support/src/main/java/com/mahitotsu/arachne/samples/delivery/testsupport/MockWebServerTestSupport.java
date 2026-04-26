package com.mahitotsu.arachne.samples.delivery.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public final class MockWebServerTestSupport {

    private MockWebServerTestSupport() {
    }

    public static void drainRequests(MockWebServer server) throws InterruptedException {
        while (server.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // drain previously recorded startup or test requests
        }
    }

    public static List<String> recordedPaths(MockWebServer server) throws InterruptedException {
        List<String> paths = new ArrayList<>();
        RecordedRequest request;
        while ((request = server.takeRequest(10, TimeUnit.MILLISECONDS)) != null) {
            paths.add(request.getPath());
        }
        return paths;
    }

    public static RecordedRequest requireRequest(MockWebServer server) {
        try {
            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            return request;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Expected downstream request", ex);
        }
    }

    public static String trimTrailingSlash(String value) {
        return value.replaceAll("/$", "");
    }
}