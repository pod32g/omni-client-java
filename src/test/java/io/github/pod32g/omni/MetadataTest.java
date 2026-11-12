package io.github.pod32g.omni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The series, label and target endpoints. */
final class MetadataTest {

    @Test
    void series() {
        String body = """
                {"status":"success","data":[
                  {"__name__":"up","job":"omni","instance":"127.0.0.1:9090"},
                  {"__name__":"up","job":"node","instance":"node-01:9100"}
                ]}""";
        try (TestServer srv = TestServer.withRoutes(Map.of("/api/v1/series", body))) {
            List<Map<String, String>> got = srv.client().series(List.of("up"), null, null);
            assertEquals(2, got.size());
            assertEquals("omni", got.get(0).get("job"));
        }
    }

    @Test
    void seriesSendsEveryMatcher() {
        AtomicReference<String> query = new AtomicReference<>();
        try (TestServer srv = TestServer.handling(exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            TestServer.respond(exchange, 200, "{\"status\":\"success\",\"data\":[]}");
        })) {
            srv.client().series(List.of("up", "node_up"), null, null);
            assertEquals("match%5B%5D=up&match%5B%5D=node_up", query.get());
        }
    }

    @Test
    void seriesRequiresMatch() {
        OmniClient c = OmniClient.builder("http://localhost:9090").build();
        assertThrows(IllegalArgumentException.class, () -> c.series(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> c.series(List.of(), null, null));
    }

    @Test
    void labelNamesAndValues() {
        Map<String, String> routes = Map.of(
                "/api/v1/labels", "{\"status\":\"success\",\"data\":[\"__name__\",\"instance\",\"job\"]}",
                "/api/v1/label/job/values", "{\"status\":\"success\",\"data\":[\"node\",\"omni\"]}");
        try (TestServer srv = TestServer.withRoutes(routes)) {
            OmniClient c = srv.client();
            assertEquals(3, c.labelNames().size());
            List<String> values = c.labelValues("job");
            assertEquals(List.of("node", "omni"), values);
            assertThrows(IllegalArgumentException.class, () -> c.labelValues(""));
        }
    }

    @Test
    void targets() {
        String body = """
                {"status":"success","data":[
                  {"job":"omni","instance":"127.0.0.1:9090","scrapeUrl":"http://127.0.0.1:9090/metrics","up":true,\
                "lastScrape":"2026-06-15T15:40:28.052269477Z","lastError":"","lastScrapeDuration":0.00086845,"samplesScraped":6},
                  {"job":"redis","instance":"cache-02:9121","scrapeUrl":"http://cache-02:9121/metrics","up":false,\
                "lastScrape":"2026-06-15T15:40:14Z","lastError":"connection refused","lastScrapeDuration":0,"samplesScraped":0}
                ]}""";
        try (TestServer srv = TestServer.withRoutes(Map.of("/api/v1/targets", body))) {
            List<Target> targets = srv.client().targets();
            assertEquals(2, targets.size());
            Target first = targets.get(0);
            assertTrue(first.up(), "target[0] should be up: " + first);
            assertEquals("omni", first.job());
            assertEquals(6, first.samplesScraped());
            Target second = targets.get(1);
            assertFalse(second.up(), "target[1] should be down: " + second);
            assertEquals("connection refused", second.lastError());
            assertNotNull(first.lastScrape());
            assertEquals(52269477, first.lastScrape().getNano());
        }
    }
}
