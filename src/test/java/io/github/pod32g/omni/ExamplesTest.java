package io.github.pod32g.omni;

import static io.github.pod32g.omni.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Port of example_test.go. Each test is the Go example's code, and checks it prints exactly the
 * Go example's "// Output:" block.
 */
final class ExamplesTest {

    /** Formats a number like Go's %g does for these values: 1, not 1.0. */
    private static String g(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    @Test
    void exampleQuery() {
        // A real program points the builder at its server, e.g. OmniClient.builder("http://localhost:9090").
        try (TestServer srv = TestServer.replying(200, "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":["
                + "{\"metric\":{\"job\":\"omni\"},\"value\":[1700000000,\"1\"]},"
                + "{\"metric\":{\"job\":\"node\"},\"value\":[1700000000,\"0\"]}]}}")) {
            OmniClient c = OmniClient.builder(srv.url()).build();
            List<String> out = new ArrayList<>();
            for (Sample s : c.query("up").vector()) {
                out.add("up{job=\"" + s.metric().get("job") + "\"} = " + g(s.value()));
            }
            assertEquals(List.of("up{job=\"omni\"} = 1", "up{job=\"node\"} = 0"), out);
        }
    }

    @Test
    void examplePush() {
        try (TestServer srv = TestServer.replying(200, "{\"status\":\"success\",\"data\":{\"samplesAppended\":2,\"seriesTouched\":2}}")) {
            OmniClient c = OmniClient.builder(srv.url()).build();

            // Build the payload from a small in-process registry.
            Registry reg = new Registry();
            reg.add("records_processed_total", null, 1500);
            reg.set("queue_depth", Map.of("queue", "high"), 12);

            PushResult res = c.push(new PushRequest("batch-importer", "worker-7", reg.series()));
            String out = "appended " + res.samplesAppended() + " samples across " + res.seriesTouched() + " series";
            assertEquals("appended 2 samples across 2 series", out);
        }
    }

    @Test
    void exampleTargets() {
        try (TestServer srv = TestServer.replying(200, "{\"status\":\"success\",\"data\":["
                + "{\"job\":\"omni\",\"instance\":\"127.0.0.1:9090\",\"up\":true},"
                + "{\"job\":\"redis\",\"instance\":\"cache-02:9121\",\"up\":false,\"lastError\":\"connection refused\"}]}")) {
            OmniClient c = OmniClient.builder(srv.url()).build();
            List<String> out = new ArrayList<>();
            for (Target t : c.targets()) {
                String state = t.up() ? "up" : "DOWN: " + t.lastError();
                out.add(t.job() + "/" + t.instance() + " " + state);
            }
            assertEquals(List.of("omni/127.0.0.1:9090 up", "redis/cache-02:9121 DOWN: connection refused"), out);
        }
    }
}
