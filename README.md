# omni-client-java

A Java port of [omni-client-go](../omni-client-go): a small, dependency-free client for the
omni-metrics HTTP API (a Prometheus-shaped metrics server). It runs queries, lists series, labels
and targets, and pushes samples.

It needs JDK 21 or newer and nothing else: no Maven, no Gradle, no libraries. The Go version used
only Go's standard library, and this port uses only Java's.

It is also teaching material. [REFACTORING.md](REFACTORING.md) has exercises that use this code.

## Build and test

```sh
./build.sh
```

The script compiles the library to `out/omni-client.jar`, then compiles and runs the 44 tests. The
tests are ported one-for-one from the Go suite, plus tests for the JSON code the Go version got from
its standard library. They need no network: each test starts a local HTTP server.

## Usage

```java
import io.github.pod32g.omni.*;
import java.time.*;
import java.util.*;

OmniClient c = OmniClient.builder("http://localhost:9090")
        .timeout(Duration.ofSeconds(5))   // optional, default 30 s
        .pushToken("token")               // optional, only sent on push
        .build();

// Instant query (no time means "now").
for (Sample s : c.query("rate(omni_http_requests_total[1m])").vector()) {
    System.out.println(s.metric() + " => " + s.value());
}

// Range query.
Instant end = Instant.now();
QueryResult m = c.queryRange("up", end.minus(Duration.ofHours(1)), end, Duration.ofSeconds(15));
for (Series series : m.matrix()) {
    System.out.println(series.metric() + " has " + series.points().size() + " points");
}

// Scrape target health.
for (Target t : c.targets()) {
    System.out.println(t.job() + "/" + t.instance() + " up=" + t.up());
}

// Push, from a small in-process registry.
Registry reg = new Registry();
reg.add("records_processed_total", null, 1500);              // counter
reg.set("queue_depth", Map.of("queue", "high"), 12);         // gauge
PushResult res = c.push(new PushRequest("batch-importer", "worker-7", reg.series()));
```

## API

| Method | Endpoint | Returns |
| --- | --- | --- |
| `query(expr)` / `query(expr, time)` | `/api/v1/query` | `QueryResult` (vector / scalar) |
| `queryRange(expr, start, end, step)` | `/api/v1/query_range` | `QueryResult` (matrix) |
| `series(matches, start, end)` | `/api/v1/series` | `List<Map<String, String>>` |
| `labelNames()` | `/api/v1/labels` | `List<String>` |
| `labelValues(name)` | `/api/v1/label/{name}/values` | `List<String>` |
| `targets()` | `/api/v1/targets` | `List<Target>` |
| `push(request)` | `/api/v1/push` | `PushResult` |

Errors are unchecked exceptions:

- `IllegalArgumentException`: a bad argument, caught before any request is sent.
- `ApiException`: the server answered with an error envelope or a non-2xx status. It has
  `statusCode()`, `type()` (for example `bad_data`) and `serverMessage()`.
- `OmniException`: anything else, such as a network failure, a timeout, an interruption or a body
  that can't be decoded. `ApiException` is a subclass.

## How the Go idioms became Java

| Go | Java |
| --- | --- |
| `omni.New(url, WithTimeout(..), WithHTTPClient(..), WithPushAuth(..))` (functional options) | `OmniClient.builder(url).timeout(..).httpClient(..).pushToken(..).build()` |
| `(result, error)` return pairs | return the result, throw an exception |
| `*APIError` and `errors.As` | `ApiException` and `catch` |
| `context.Context` for cancellation | a per-request timeout, or interrupting the calling thread |
| `time.Time{}` (zero time) meaning "not set" | `null` |
| `*float64` meaning "optional" | `Double` (nullable) |
| structs with `json:"..."` tags and `json.Unmarshal` | records, plus explicit decoders in `Decoders.java` and a small `Json.java` |
| `sync.Mutex` | `synchronized` |
| `httptest.NewServer` | `com.sun.net.httpserver.HttpServer` (see `TestServer.java`) |
| `go test` | `./build.sh` with a ~60-line `TestRunner` that uses JUnit's names |

## Differences from the Go version

- **No `context.Context`.** Cancel a request with the builder's `timeout`, or by interrupting the
  calling thread. Both are tested.
- **Times are sent as exact decimals.** Go converted them through a `float64`, which is accurate to
  about a microsecond. Java uses `BigDecimal`, so nanoseconds survive.
- **A caller's `HttpClient` is used as is.** Go had to copy the caller's `http.Client` so that
  setting a timeout wouldn't change it. Java's `HttpClient` can't be changed after it is built, so
  the port applies the timeout to each request instead.
- **Results are unmodifiable.** Lists and label maps in results can't be changed by the caller.

Checked against the live omni-metrics on 2026-11-12 with read-only calls only: `query`,
`queryRange`, `labelNames`, `targets`, and an invalid query that returned a `bad_data`
`ApiException`.
