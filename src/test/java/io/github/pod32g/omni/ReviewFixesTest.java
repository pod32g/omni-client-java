package io.github.pod32g.omni;

import static io.github.pod32g.omni.Assertions.assertEquals;
import static io.github.pod32g.omni.Assertions.assertThrows;
import static io.github.pod32g.omni.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Port of review_fixes_test.go: one test per finding of the Go version's code review. */
final class ReviewFixesTest {

    // Finding #1: option order must not matter, and the timeout must never change a caller's
    // client. In Go that needed a defensive copy; Java's HttpClient is immutable, so the library
    // can keep the caller's instance and apply the timeout per request instead.
    @Test
    void optionsOrderIndependentAndNoSharedMutation() {
        HttpClient shared = HttpClient.newHttpClient();

        OmniClient c1 = OmniClient.builder("http://h:9090").timeout(Duration.ofSeconds(7)).httpClient(shared).build();
        assertEquals(Duration.ofSeconds(7), c1.timeout());

        OmniClient c2 = OmniClient.builder("http://h:9090").httpClient(shared).timeout(Duration.ofSeconds(5)).build();
        assertEquals(Duration.ofSeconds(5), c2.timeout());

        assertTrue(shared.connectTimeout().isEmpty(), "the caller's client was changed");
        assertTrue(c1.httpClient() == shared && c2.httpClient() == shared, "the caller's client should be used as is");
    }

    // Finding #2: any non-2xx reply surfaces as a typed ApiException carrying the status code,
    // even when the body is not the JSON envelope.
    @Test
    void non2xxNonJsonIsApiError() {
        record Case(int code, String body) {
        }
        for (Case tc : new Case[] {
                new Case(502, "<html>502 Bad Gateway</html>"),
                new Case(401, "Unauthorized"),
                new Case(503, "")}) {
            try (TestServer srv = TestServer.replying(tc.code(), tc.body())) {
                ApiException e = assertThrows(ApiException.class, () -> srv.client().get("/x", Map.of(), null));
                assertEquals(tc.code(), e.statusCode());
            }
        }
    }

    // Finding #6: an over-large body is an explicit error, not a confusing decode failure.
    @Test
    void oversizedBodyError() {
        try (TestServer srv = TestServer.replying(200, "a".repeat(1000))) {
            OmniClient c = OmniClient.builder(srv.url()).maxBody(100).build(); // far below the reply
            ApiException e = assertThrows(ApiException.class, () -> c.get("/x", Map.of(), null));
            assertTrue(e.serverMessage().contains("exceeds"), "want an explicit size error, got: " + e.getMessage());
        }
    }

    // Finding #5: a base URL with a query/fragment must not corrupt request URLs.
    @Test
    void newNormalizesBaseUrl() {
        OmniClient c = OmniClient.builder("http://h:9090/prefix?x=1#frag").build();
        assertEquals("http://h:9090/prefix", c.baseUrl());
        assertThrows(IllegalArgumentException.class, () -> OmniClient.builder("ftp://h:9090").build());
    }

    // Finding #3: queryRange must reject missing or inverted time bounds client-side.
    @Test
    void queryRangeValidation() {
        OmniClient c = OmniClient.builder("http://h:9090").build();
        Duration second = Duration.ofSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> c.queryRange("up", null, Instant.ofEpochSecond(1), second));
        assertThrows(IllegalArgumentException.class, () -> c.queryRange("up", Instant.ofEpochSecond(1), null, second));
        assertThrows(IllegalArgumentException.class,
                () -> c.queryRange("up", Instant.ofEpochSecond(2), Instant.ofEpochSecond(1), second));
    }

    // Finding #4: time formatting must not be capped at milliseconds. (Go used a float64 and
    // checked ~100.123456; BigDecimal makes it exact.)
    @Test
    void formatTimeNotMillisecondCapped() {
        assertEquals("100.123456", Times.format(Instant.ofEpochSecond(100, 123_456_000)));
        assertEquals("1781538033", Times.format(Instant.ofEpochSecond(1781538033)));
    }
}
