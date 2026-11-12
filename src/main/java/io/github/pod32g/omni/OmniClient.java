package io.github.pod32g.omni;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * A client for the omni-metrics HTTP API, a Prometheus-shaped metrics server. It wraps the
 * {@code /api/v1} query and metadata endpoints with typed results and exceptions.
 *
 * <p>Create one with {@link #builder(String)}, then call {@link #query}, {@link #queryRange},
 * {@link #series}, {@link #labelNames}, {@link #labelValues} or {@link #targets} to read. To write,
 * use {@link #push} (for a process that has no HTTP server to be scraped), optionally building the
 * payload from a {@link Registry}.
 *
 * <p>Errors: invalid arguments throw {@link IllegalArgumentException}; a server-side error or a
 * non-2xx reply throws {@link ApiException}; anything else (network, timeout, undecodable body)
 * throws {@link OmniException}.
 *
 * <p>Instances are immutable and safe for concurrent use.
 */
public final class OmniClient {

    /** Bounds a response body to guard against an unbounded payload. */
    static final int DEFAULT_MAX_BODY = 64 << 20;

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /** Shared by every client that doesn't bring its own, like Go's DefaultTransport. */
    private static final HttpClient DEFAULT_HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration timeout; // null means no per-request timeout
    private final String pushToken; // "" means no Authorization header
    private final int maxBody;

    private OmniClient(Builder b, String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = b.httpClient;
        this.timeout = (b.timeout == null || b.timeout.isZero() || b.timeout.isNegative()) ? null : b.timeout;
        this.pushToken = b.pushToken;
        this.maxBody = b.maxBody;
    }

    /** Starts building a client for the server at {@code baseUrl}, e.g. {@code "http://localhost:9090"}. */
    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }

    /**
     * Configures an {@link OmniClient}. Setters are independent of order. This replaces Go's
     * functional options ({@code WithTimeout}, {@code WithHTTPClient}, {@code WithPushAuth}).
     */
    public static final class Builder {
        private final String baseUrl;
        private HttpClient httpClient = DEFAULT_HTTP_CLIENT;
        private Duration timeout = DEFAULT_TIMEOUT;
        private String pushToken = "";
        private int maxBody = DEFAULT_MAX_BODY;

        private Builder(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * Uses this {@link HttpClient} (custom executor, TLS, proxy, ...). An HttpClient is
         * immutable, so the library can share the caller's instance without ever changing it. A
         * null client is ignored.
         */
        public Builder httpClient(HttpClient client) {
            if (client != null) {
                this.httpClient = client;
            }
            return this;
        }

        /**
         * Sets the per-request timeout (default 30 s). Null, zero or negative means no timeout. It
         * is applied to each request, never to the HttpClient, so it works with any client.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets a bearer token sent on push writes ({@code POST /api/v1/push}) as
         * {@code Authorization: Bearer <token>}. Read requests are unaffected. Null or empty means
         * no token.
         */
        public Builder pushToken(String token) {
            this.pushToken = token == null ? "" : token;
            return this;
        }

        /** Lowers the response body limit. Package-private: only tests need it. */
        Builder maxBody(int bytes) {
            this.maxBody = bytes;
            return this;
        }

        /**
         * Builds the client. Any query string or fragment on the base URL is dropped; a path is
         * kept (useful behind a reverse proxy).
         *
         * @throws IllegalArgumentException if the base URL is missing, not http(s), or has no host
         */
        public OmniClient build() {
            return new OmniClient(this, normalizeBaseUrl(baseUrl));
        }
    }

    // ---------------------------------------------------------------- reads: queries

    /** Evaluates {@code expr} now. */
    public QueryResult query(String expr) {
        return query(expr, null);
    }

    /** Evaluates {@code expr} at a single instant. A null {@code time} lets the server use "now". */
    public QueryResult query(String expr, Instant time) {
        Objects.requireNonNull(expr, "expr");
        Map<String, List<String>> params = new TreeMap<>();
        params.put("query", List.of(expr));
        if (time != null) {
            params.put("time", List.of(Times.format(time)));
        }
        return get("/api/v1/query", params, Decoders::queryResult);
    }

    /**
     * Evaluates {@code expr} at each step over [start, end]. Start and end are required (a range
     * query needs a real window) and step must be positive.
     */
    public QueryResult queryRange(String expr, Instant start, Instant end, Duration step) {
        Objects.requireNonNull(expr, "expr");
        if (step == null || step.isZero() || step.isNegative()) {
            throw new IllegalArgumentException("omni: step must be positive");
        }
        if (start == null || end == null) {
            throw new IllegalArgumentException("omni: start and end are required");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("omni: end must not be before start");
        }
        Map<String, List<String>> params = new TreeMap<>();
        params.put("query", List.of(expr));
        params.put("start", List.of(Times.format(start)));
        params.put("end", List.of(Times.format(end)));
        params.put("step", List.of(Times.seconds(step)));
        return get("/api/v1/query_range", params, Decoders::queryResult);
    }

    // ---------------------------------------------------------------- reads: metadata

    /**
     * Returns the label sets of series matching the given selectors (e.g. {@code up},
     * {@code http_requests_total{job="api"}}). A null start or end lets the server use its default
     * window.
     */
    public List<Map<String, String>> series(List<String> matches, Instant start, Instant end) {
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("omni: at least one match selector is required");
        }
        Map<String, List<String>> params = new TreeMap<>();
        params.put("match[]", List.copyOf(matches));
        if (start != null) {
            params.put("start", List.of(Times.format(start)));
        }
        if (end != null) {
            params.put("end", List.of(Times.format(end)));
        }
        return get("/api/v1/series", params, Decoders::labelSets);
    }

    /** Returns all label names present in the server's series. */
    public List<String> labelNames() {
        return get("/api/v1/labels", Map.of(), Decoders::strings);
    }

    /** Returns the distinct values of a single label. */
    public List<String> labelValues(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("omni: label name is required");
        }
        return get("/api/v1/label/" + pathEscape(name) + "/values", Map.of(), Decoders::strings);
    }

    /** Returns the scrape target health. */
    public List<Target> targets() {
        return get("/api/v1/targets", Map.of(), Decoders::targets);
    }

    // ---------------------------------------------------------------- writes: push

    /**
     * Sends {@code request} to the server's {@code /api/v1/push} endpoint. It validates the request
     * locally (job and at least one series required; each series needs a name and exactly one of
     * value or samples) before sending, and returns the server's append counts.
     */
    public PushResult push(PushRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("omni: push request is null");
        }
        if (request.job() == null || request.job().isEmpty()) {
            throw new IllegalArgumentException("omni: push job is required");
        }
        List<PushSeries> series = request.series();
        if (series == null || series.isEmpty()) {
            throw new IllegalArgumentException("omni: push requires at least one series");
        }

        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("job", request.job());
        if (request.instance() != null && !request.instance().isEmpty()) {
            wire.put("instance", request.instance());
        }
        List<Object> wireSeries = new ArrayList<>(series.size());
        for (int i = 0; i < series.size(); i++) {
            PushSeries s = series.get(i);
            if (s == null || s.name() == null || s.name().isEmpty()) {
                throw new IllegalArgumentException("omni: series[" + i + "]: name is required");
            }
            boolean hasValue = s.value() != null;
            boolean hasSamples = s.samples() != null && !s.samples().isEmpty();
            if (hasValue == hasSamples) {
                throw new IllegalArgumentException(String.format(
                        "omni: series[%d] \"%s\": set exactly one of value or samples", i, s.name()));
            }
            Map<String, Object> ws = new LinkedHashMap<>();
            ws.put("name", s.name());
            if (s.labels() != null && !s.labels().isEmpty()) {
                ws.put("labels", new TreeMap<>(s.labels()));
            }
            if (hasValue) {
                ws.put("value", wireValue(s.value()));
            } else {
                List<Object> samples = new ArrayList<>(s.samples().size());
                for (PushSample sample : s.samples()) {
                    Map<String, Object> w = new LinkedHashMap<>();
                    long ms = sample.timestamp() == null ? 0 : sample.timestamp().toEpochMilli();
                    if (ms != 0) { // omitted, so the server stamps it at receive time
                        w.put("timestamp_ms", ms);
                    }
                    w.put("value", wireValue(sample.value()));
                    samples.add(w);
                }
                ws.put("samples", samples);
            }
            wireSeries.add(ws);
        }
        wire.put("series", wireSeries);
        return post("/api/v1/push", wire, Decoders::pushResult);
    }

    /**
     * A float as a JSON number, or as the strings "NaN"/"+Inf"/"-Inf" for non-finite values (which
     * JSON numbers cannot represent and which the server decodes back to the matching float).
     */
    private static Object wireValue(double v) {
        if (Double.isNaN(v)) {
            return "NaN";
        }
        if (v == Double.POSITIVE_INFINITY) {
            return "+Inf";
        }
        if (v == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        }
        return v;
    }

    // ---------------------------------------------------------------- HTTP plumbing

    /**
     * Performs a GET, validates the {status} envelope, and decodes data with {@code decoder} (which
     * may be null to ignore it). Package-private so tests can call it directly.
     */
    <T> T get(String path, Map<String, List<String>> params, Function<Object, T> decoder) {
        String url = baseUrl + path;
        if (!params.isEmpty()) {
            url += "?" + encodeQuery(params);
        }
        return send(newRequest(url).GET().build(), decoder);
    }

    /** POSTs {@code payload} as JSON (with the push bearer token when set) and decodes the reply. */
    private <T> T post(String path, Object payload, Function<Object, T> decoder) {
        HttpRequest.Builder request = newRequest(baseUrl + path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(payload), StandardCharsets.UTF_8));
        if (!pushToken.isEmpty()) {
            request.header("Authorization", "Bearer " + pushToken);
        }
        return send(request.build(), decoder);
    }

    private HttpRequest.Builder newRequest(String url) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/json");
        if (timeout != null) {
            b.timeout(timeout);
        }
        return b;
    }

    /**
     * Sends the request, enforces the body limit, validates the {status} envelope, and decodes
     * data. Shared by get and post.
     */
    private <T> T send(HttpRequest request, Function<Object, T> decoder) {
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OmniException("omni: request interrupted", e);
        } catch (IOException e) {
            throw new OmniException("omni: request failed: " + e, e);
        }
        int status = response.statusCode();

        // Read one byte past the limit so an over-large body is detectable rather than silently
        // truncated into a confusing decode error.
        byte[] body;
        try (InputStream in = response.body()) {
            body = in.readNBytes(maxBody + 1);
        } catch (IOException e) {
            throw new OmniException("omni: reading response: " + e, e);
        }
        if (body.length > maxBody) {
            throw new ApiException(status, "", "response exceeds " + maxBody + " byte limit");
        }

        Map<String, Object> envelope;
        String envStatus;
        String errorType;
        String error;
        try {
            envelope = Json.object(Json.parse(new String(body, StandardCharsets.UTF_8)), "response");
            envStatus = Json.string(envelope.get("status"), "status");
            errorType = Json.string(envelope.get("errorType"), "errorType");
            error = Json.string(envelope.get("error"), "error");
        } catch (JsonException e) {
            // A non-2xx response whose body is not the envelope (a proxy's HTML 502, a plain-text
            // 401, an empty 503, ...) still surfaces as a typed ApiException carrying the status.
            if (status / 100 != 2) {
                throw new ApiException(status, "", statusMessage(status, body));
            }
            throw new OmniException("omni: decoding response (HTTP " + status + "): " + e.getMessage(), e);
        }
        if (!envStatus.equals("success")) {
            throw new ApiException(status, errorType, error);
        }
        if (decoder == null) {
            return null;
        }
        if (!envelope.containsKey("data")) {
            throw new OmniException("omni: decoding data: response has no \"data\"");
        }
        try {
            return decoder.apply(envelope.get("data"));
        } catch (JsonException e) {
            throw new OmniException("omni: decoding data: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- helpers

    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("omni: baseURL is required");
        }
        URI u;
        try {
            u = new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("omni: invalid baseURL: " + e.getMessage(), e);
        }
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("omni: baseURL scheme must be http or https, got \"" + baseUrl + "\"");
        }
        String authority = u.getRawAuthority();
        if (authority == null || authority.isEmpty()) {
            throw new IllegalArgumentException("omni: baseURL must include a host, got \"" + baseUrl + "\"");
        }
        // Drop any query/fragment so request URLs (built by concatenation) can't be corrupted;
        // keep scheme, userinfo, host, port and path.
        String path = u.getRawPath() == null ? "" : u.getRawPath();
        String normalized = scheme + "://" + authority + path;
        int end = normalized.length();
        while (end > 0 && normalized.charAt(end - 1) == '/') {
            end--;
        }
        return normalized.substring(0, end);
    }

    /** Encodes query parameters sorted by key, like Go's url.Values.Encode. */
    private static String encodeQuery(Map<String, List<String>> params) {
        StringJoiner query = new StringJoiner("&");
        new TreeMap<>(params).forEach((key, values) -> {
            for (String value : values) {
                query.add(formEncode(key) + "=" + formEncode(value));
            }
        });
        return query.toString();
    }

    private static String formEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String pathEscape(String s) {
        return formEncode(s).replace("+", "%20");
    }

    /**
     * Builds an ApiException message for a non-envelope error response, preferring a trimmed body
     * snippet and falling back to the HTTP status text.
     */
    static String statusMessage(int status, byte[] body) {
        String s = new String(body, StandardCharsets.UTF_8).strip();
        if (s.isEmpty()) {
            return statusText(status);
        }
        int max = 200;
        if (s.length() > max) {
            int cut = Character.isHighSurrogate(s.charAt(max - 1)) ? max - 1 : max;
            s = s.substring(0, cut) + "…";
        }
        return s;
    }

    private static String statusText(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 413 -> "Request Entity Too Large";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "";
        };
    }

    // Package-private accessors for tests.

    String baseUrl() {
        return baseUrl;
    }

    HttpClient httpClient() {
        return httpClient;
    }

    Duration timeout() {
        return timeout;
    }
}
