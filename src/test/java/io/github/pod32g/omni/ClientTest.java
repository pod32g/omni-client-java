package io.github.pod32g.omni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Building a client, error replies, and cancelling a request. */
final class ClientTest {

    private static final String EMPTY_LABELS = "{\"status\":\"success\",\"data\":[]}";

    @Test
    void newValidation() {
        assertThrows(IllegalArgumentException.class, () -> OmniClient.builder("").build());
        assertThrows(IllegalArgumentException.class, () -> OmniClient.builder("notaurl").build());
        OmniClient c = OmniClient.builder("http://localhost:9090/").build();
        assertEquals("http://localhost:9090", c.baseUrl());
    }

    @Test
    void getReturnsApiError() {
        String body = "{\"status\":\"error\",\"errorType\":\"bad_data\",\"error\":\"parse error: unexpected token\"}";
        try (TestServer srv = TestServer.replying(400, body)) {
            ApiException e = assertThrows(ApiException.class, () -> srv.client().get("/api/v1/query", Map.of(), null));
            assertEquals("bad_data", e.type());
            assertEquals(400, e.statusCode());
            assertFalse(e.getMessage().isEmpty(), "ApiException.getMessage() should be non-empty");
        }
    }

    // A request can be cut short by a timeout or by interrupting the calling thread; both are
    // tested.

    @Test
    void timeoutCancelsRequest() {
        CountDownLatch release = new CountDownLatch(1);
        try (TestServer srv = stallingServer(release)) {
            try {
                OmniClient c = OmniClient.builder(srv.url()).timeout(Duration.ofMillis(200)).build();
                OmniException e = assertThrows(OmniException.class, c::labelNames);
                assertTrue(e.getMessage().contains("timed out"), "want a timeout, got: " + e.getMessage());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void interruptCancelsRequest() {
        CountDownLatch release = new CountDownLatch(1);
        try (TestServer srv = stallingServer(release)) {
            try {
                OmniClient c = OmniClient.builder(srv.url()).timeout(Duration.ofSeconds(5)).build();
                Thread.currentThread().interrupt();
                OmniException e = assertThrows(OmniException.class, c::labelNames);
                assertTrue(e.getMessage().contains("interrupted"), "want an interruption, got: " + e.getMessage());
                assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag should be restored");
            } finally {
                Thread.interrupted(); // clear the flag for the next test
                release.countDown();
            }
        }
    }

    /** A server that holds every request until {@code release} opens. */
    private static TestServer stallingServer(CountDownLatch release) {
        return TestServer.handling(exchange -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            TestServer.respond(exchange, 200, EMPTY_LABELS);
        });
    }
}
