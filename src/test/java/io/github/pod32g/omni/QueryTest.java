package io.github.pod32g.omni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Instant and range queries. */
final class QueryTest {

    @Test
    void queryVector() {
        String body = """
                {"status":"success","data":{"resultType":"vector","result":[
                  {"metric":{"__name__":"up","job":"omni","instance":"127.0.0.1:9090"},"value":[1781538033.843,"1"]},
                  {"metric":{"__name__":"up","job":"node","instance":"node-01:9100"},"value":[1781538033.843,"0"]}
                ]}}""";
        try (TestServer srv = TestServer.withRoutes(Map.of("/api/v1/query", body))) {
            QueryResult res = srv.client().query("up", Instant.ofEpochSecond(1781538033));
            assertEquals(ResultType.VECTOR, res.type());
            assertEquals(2, res.vector().size());
            assertEquals(1, res.vector().get(0).value());
            assertEquals("omni", res.vector().get(0).metric().get("job"));
            assertEquals(0, res.vector().get(1).value());
        }
    }

    @Test
    void queryScalar() {
        String body = "{\"status\":\"success\",\"data\":{\"resultType\":\"scalar\",\"result\":[1781538033.843,\"42\"]}}";
        try (TestServer srv = TestServer.withRoutes(Map.of("/api/v1/query", body))) {
            QueryResult res = srv.client().query("42");
            assertEquals(ResultType.SCALAR, res.type());
            assertNotNull(res.scalar());
            assertEquals(42, res.scalar().value());
        }
    }

    @Test
    void queryRangeMatrix() {
        String body = """
                {"status":"success","data":{"resultType":"matrix","result":[
                  {"metric":{"job":"omni"},"values":[[1000,"10"],[2000,"20"],[3000,"30"]]}
                ]}}""";
        try (TestServer srv = TestServer.withRoutes(Map.of("/api/v1/query_range", body))) {
            QueryResult res = srv.client().queryRange("m{job=\"omni\"}",
                    Instant.ofEpochSecond(1000), Instant.ofEpochSecond(3000), Duration.ofSeconds(15));
            assertEquals(ResultType.MATRIX, res.type());
            assertEquals(1, res.matrix().size());
            List<Point> points = res.matrix().get(0).points();
            assertEquals(3, points.size());
            assertEquals(10, points.get(0).value());
            assertEquals(30, points.get(2).value());
        }
    }

    @Test
    void queryRangeSendsSortedEncodedParams() {
        AtomicReference<String> query = new AtomicReference<>();
        try (TestServer srv = TestServer.handling(exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            TestServer.respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[]}}");
        })) {
            srv.client().queryRange("m{job=\"omni\"}",
                    Instant.ofEpochSecond(1000), Instant.ofEpochSecond(3000), Duration.ofMillis(15_500));
            assertEquals("end=3000&query=m%7Bjob%3D%22omni%22%7D&start=1000&step=15.5", query.get());
        }
    }

    @Test
    void queryRangeRejectsBadStep() {
        OmniClient c = OmniClient.builder("http://localhost:9090").build();
        assertThrows(IllegalArgumentException.class,
                () -> c.queryRange("up", Instant.ofEpochSecond(0), Instant.ofEpochSecond(1), Duration.ZERO));
    }

    @Test
    void queryPropagatesApiError() {
        String body = "{\"status\":\"error\",\"errorType\":\"bad_data\",\"error\":\"boom\"}";
        try (TestServer srv = TestServer.withRoutes(Map.of("/api/v1/query", body))) {
            assertThrows(ApiException.class, () -> srv.client().query("bad{"));
        }
    }

    @Test
    void queryRejectsUnknownResultType() {
        String body = "{\"status\":\"success\",\"data\":{\"resultType\":\"string\",\"result\":[1,\"x\"]}}";
        try (TestServer srv = TestServer.withRoutes(Map.of("/api/v1/query", body))) {
            OmniException e = assertThrows(OmniException.class, () -> srv.client().query("\"x\""));
            assertEquals("omni: unknown result type \"string\"", e.getMessage());
        }
    }
}
