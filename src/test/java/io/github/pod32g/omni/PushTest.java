package io.github.pod32g.omni;

import static io.github.pod32g.omni.Assertions.assertEquals;
import static io.github.pod32g.omni.Assertions.assertFalse;
import static io.github.pod32g.omni.Assertions.assertNull;
import static io.github.pod32g.omni.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Port of push_test.go. */
final class PushTest {

    /** What the echo server saw. Checked on the test thread, not inside the server's handler. */
    private record Received(String method, String path, String authorization, Map<String, Object> body) {
    }

    /** A server that records the push it receives and answers with fixed counts. */
    private static TestServer pushEcho(AtomicReference<Received> received) {
        return TestServer.handling(exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.set(new Received(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    Json.object(Json.parse(body), "push body")));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            TestServer.respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"samplesAppended\":3,\"seriesTouched\":2}}");
        });
    }

    private static Map<String, Object> obj(Object v) {
        return Json.object(v, "test");
    }

    private static List<Object> arr(Object v) {
        return Json.array(v, "test");
    }

    @Test
    void pushSendsWireShape() {
        AtomicReference<Received> received = new AtomicReference<>();
        try (TestServer srv = pushEcho(received)) {
            PushResult res = srv.client().push(new PushRequest("batch", "worker-7", List.of(
                    PushSeries.ofValue("records_total", null, 1500),
                    PushSeries.ofSamples("latency", null, List.of(
                            new PushSample(Instant.ofEpochMilli(1000), 0.1),
                            PushSample.of(0.2))))));
            assertEquals(3, res.samplesAppended());
            assertEquals(2, res.seriesTouched());

            Received got = received.get();
            assertEquals("POST", got.method());
            assertEquals("/api/v1/push", got.path());
            assertNull(got.authorization());
            assertEquals("batch", got.body().get("job"));
            assertEquals("worker-7", got.body().get("instance"));

            List<Object> series = arr(got.body().get("series"));
            assertEquals(2, series.size());
            Map<String, Object> s0 = obj(series.get(0));
            assertEquals("records_total", s0.get("name"));
            assertEquals(1500.0, s0.get("value"));

            List<Object> samples = arr(obj(series.get(1)).get("samples"));
            assertEquals(2, samples.size());
            assertEquals(1000.0, obj(samples.get(0)).get("timestamp_ms"));
            // A missing timestamp must be omitted so the server stamps it at receive time.
            assertFalse(obj(samples.get(1)).containsKey("timestamp_ms"), "zero timestamp should be omitted: " + samples.get(1));
        }
    }

    @Test
    void pushEncodesNonFinite() {
        AtomicReference<Received> received = new AtomicReference<>();
        try (TestServer srv = pushEcho(received)) {
            srv.client().push(new PushRequest("j", List.of(
                    PushSeries.ofValue("ceiling", null, Double.POSITIVE_INFINITY),
                    PushSeries.ofValue("missing", null, Double.NaN))));
            List<Object> series = arr(received.get().body().get("series"));
            assertEquals("+Inf", obj(series.get(0)).get("value"));
            assertEquals("NaN", obj(series.get(1)).get("value"));
        }
    }

    @Test
    void pushAuthHeader() {
        AtomicReference<Received> received = new AtomicReference<>();
        try (TestServer srv = pushEcho(received)) {
            OmniClient c = OmniClient.builder(srv.url()).pushToken("s3cr3t").build();
            c.push(new PushRequest("j", List.of(PushSeries.ofValue("a", null, 1))));
            assertEquals("Bearer s3cr3t", received.get().authorization());
        }
    }

    @Test
    void pushClientValidation() {
        OmniClient c = OmniClient.builder("http://127.0.0.1:0").build();
        PushSeries ok = PushSeries.ofValue("a", null, 1);
        record Case(String name, PushRequest request) {
        }
        List<Case> cases = List.of(
                new Case("null", null),
                new Case("empty job", new PushRequest("", List.of(ok))),
                new Case("no series", new PushRequest("j", List.of())),
                new Case("empty name", new PushRequest("j", List.of(PushSeries.ofValue("", null, 1)))),
                new Case("both value and samples",
                        new PushRequest("j", List.of(new PushSeries("a", null, 1.0, List.of(PushSample.of(1)))))),
                new Case("neither", new PushRequest("j", List.of(new PushSeries("a", null, null, null)))));
        for (Case tc : cases) {
            assertThrows(IllegalArgumentException.class, () -> c.push(tc.request()));
        }
    }

    @Test
    void pushSurfacesApiError() {
        String body = "{\"status\":\"error\",\"errorType\":\"bad_data\",\"error\":\"job must not be empty\"}";
        try (TestServer srv = TestServer.replying(400, body)) {
            ApiException e = assertThrows(ApiException.class,
                    () -> srv.client().push(new PushRequest("j", List.of(PushSeries.ofValue("a", null, 1)))));
            assertEquals("bad_data", e.type());
        }
    }
}
