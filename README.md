# omni-client-java

A small Java client for **omni-metrics**, a metrics server with a Prometheus-compatible HTTP API.
Use it to read metrics: run PromQL queries, list series and labels, and check scrape targets. Use it
to write them too: a program that has no HTTP endpoint for the server to scrape can push its own
samples.

The library needs Java 21 or newer and has no runtime dependencies beyond the JDK.

The code is small and fully tested, so it also works as a place to practise refactoring.
[REFACTORING.md](REFACTORING.md) has exercises.

## Build and test

Nothing needs to be installed except a JDK. The Gradle wrapper (`./gradlew`, or `gradlew.bat` on
Windows) downloads the right Gradle version the first time it runs.

```sh
./gradlew build    # compile, run the tests, and build the jar
./gradlew test     # just the tests
```

The jar ends up in `build/libs/`. Gradle compiles and tests with JDK 21 and downloads it
automatically if the machine doesn't have it.

The tests need no network and no running server: each one starts a small local HTTP server. The
test run ends with a one-line total, such as `44 tests: 44 passed, 0 failed, 0 skipped`. Gradle
skips tests that already passed when nothing has changed, so use `./gradlew test --rerun` to run
them again anyway.

To work in an IDE, open the folder in IntelliJ IDEA (or VS Code with the Java extensions). It is
imported as a Gradle project, and every test can be run from the editor.

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

// Range query: one value per step over the last hour.
Instant end = Instant.now();
QueryResult m = c.queryRange("up", end.minus(Duration.ofHours(1)), end, Duration.ofSeconds(15));
for (Series series : m.matrix()) {
    System.out.println(series.metric() + " has " + series.points().size() + " points");
}

// Scrape target health.
for (Target t : c.targets()) {
    System.out.println(t.job() + "/" + t.instance() + " up=" + t.up());
}
```

A client is immutable and safe to share between threads. Create one per server and reuse it.

## API

| Method | Endpoint | Returns |
| --- | --- | --- |
| `query(expr)` / `query(expr, time)` | `/api/v1/query` | `QueryResult` (vector or scalar) |
| `queryRange(expr, start, end, step)` | `/api/v1/query_range` | `QueryResult` (matrix) |
| `series(matches, start, end)` | `/api/v1/series` | `List<Map<String, String>>` |
| `labelNames()` | `/api/v1/labels` | `List<String>` |
| `labelValues(name)` | `/api/v1/label/{name}/values` | `List<String>` |
| `targets()` | `/api/v1/targets` | `List<Target>` |
| `push(request)` | `/api/v1/push` | `PushResult` |

`QueryResult.type()` says which shape came back: `VECTOR` fills `vector()`, `MATRIX` fills
`matrix()`, and `SCALAR` fills `scalar()`. Values are `double`s and may be `+Inf`, `-Inf` or `NaN`.
Timestamps are `Instant`s.

## Pushing metrics

Each push **appends** samples, building a time series on the server, so `rate()` works on pushed
counters. Build the series by hand or keep them in a `Registry`:

```java
Registry reg = new Registry();
reg.add("records_processed_total", null, 1500);          // counter: add to it
reg.set("queue_depth", Map.of("queue", "high"), 12);     // gauge: set it

PushResult res = c.push(new PushRequest(
        "batch-importer",   // job, required
        "worker-7",         // instance, optional: the server defaults it to the caller's host
        reg.series()));
System.out.println(res.samplesAppended() + " samples appended");
```

Each series carries exactly one of the following:

- a single value, which the server stamps when it receives it: `PushSeries.ofValue(...)`
- explicit samples with their own timestamps: `PushSeries.ofSamples(...)`

The server adds the `job` and `instance` labels itself. A client can't override `__name__`, `job`
or `instance`.

## Errors

All errors are unchecked exceptions:

- `IllegalArgumentException`: a bad argument, found before any request is sent.
- `ApiException`: the server replied with an error or a non-2xx status. It has `statusCode()`,
  `type()` (for example `bad_data`) and `serverMessage()`.
- `OmniException`: anything else, such as a network failure, a timeout, an interrupted thread or a
  reply that can't be decoded. `ApiException` is a subclass of it.

```java
try {
    c.query("bad{");
} catch (ApiException e) {
    System.out.println(e.type() + ": " + e.serverMessage());   // bad_data: ...
}
```

To cancel a slow request, set a timeout on the builder or interrupt the thread that is waiting for
it.

## How the code is organised

Everything lives in the package `io.github.pod32g.omni` under `src/main/java`.

| File | What it does |
| --- | --- |
| `OmniClient` | The public entry point: builds requests, checks arguments, sends them, and unwraps the server's `{"status": ..., "data": ...}` reply. |
| `Decoders` | Turns each endpoint's JSON `data` into the result records. |
| `Json` | A small JSON reader and writer, so the library needs no JSON dependency. |
| `Times` | Converts between `Instant`/`Duration` and the API's decimal Unix seconds. |
| `Registry` | The in-process store of counters and gauges for pushing. |
| `QueryResult`, `Sample`, `Series`, `Point`, `Scalar`, `Target`, `PushRequest`, `PushSeries`, `PushSample`, `PushResult` | Records that carry the data in and out. |
| `OmniException`, `ApiException` | The error types. |

The tests in `src/test/java` use JUnit, plus `TestServer`, a small local HTTP server built on the
JDK's own `com.sun.net.httpserver`.
