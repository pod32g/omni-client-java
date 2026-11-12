package io.github.pod32g.omni;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Turns the parsed JSON of each endpoint's {@code data} into the public record types. Go did this
 * with struct tags and {@code json.Unmarshal}; Java's standard library has no JSON binding, so it is
 * spelled out here. Shape problems throw {@link JsonException}.
 */
final class Decoders {
    private Decoders() {
    }

    // ---------------------------------------------------------------- queries

    static QueryResult queryResult(Object data) {
        Map<String, Object> qd = Json.object(data, "query data");
        String typeName = Json.string(qd.get("resultType"), "resultType");
        ResultType type = ResultType.fromWireName(typeName);
        if (type == null) {
            throw new OmniException("omni: unknown result type \"" + typeName + "\"");
        }
        Object result = qd.get("result");
        return switch (type) {
            case VECTOR -> new QueryResult(type, vector(result), List.of(), null);
            case MATRIX -> new QueryResult(type, List.of(), matrix(result), null);
            case SCALAR -> new QueryResult(type, List.of(), List.of(), scalar(result));
        };
    }

    private static List<Sample> vector(Object result) {
        try {
            List<Sample> out = new ArrayList<>();
            for (Object item : Json.array(result, "vector")) {
                Map<String, Object> s = Json.object(item, "sample");
                SamplePair v = samplePair(s.get("value"));
                out.add(new Sample(labels(s.get("metric")), v.timestamp(), v.value()));
            }
            return Collections.unmodifiableList(out);
        } catch (JsonException e) {
            throw new JsonException("decoding vector: " + e.getMessage());
        }
    }

    private static List<Series> matrix(Object result) {
        try {
            List<Series> out = new ArrayList<>();
            for (Object item : Json.array(result, "matrix")) {
                Map<String, Object> s = Json.object(item, "series");
                List<Point> points = new ArrayList<>();
                for (Object raw : Json.array(s.get("values"), "values")) {
                    SamplePair v = samplePair(raw);
                    points.add(new Point(v.timestamp(), v.value()));
                }
                out.add(new Series(labels(s.get("metric")), Collections.unmodifiableList(points)));
            }
            return Collections.unmodifiableList(out);
        } catch (JsonException e) {
            throw new JsonException("decoding matrix: " + e.getMessage());
        }
    }

    private static Scalar scalar(Object result) {
        try {
            SamplePair v = samplePair(result);
            return new Scalar(v.timestamp(), v.value());
        } catch (JsonException e) {
            throw new JsonException("decoding scalar: " + e.getMessage());
        }
    }

    /** A decoded {@code [<unix_seconds_float>, "<value_string>"]} pair. */
    record SamplePair(Instant timestamp, double value) {
    }

    /**
     * Parses Prometheus' {@code [<unix_seconds_float>, "<value_string>"]} pair into a timestamp and
     * a double (the string value may be "+Inf", "-Inf" or "NaN").
     */
    static SamplePair samplePair(Object raw) {
        List<Object> pair = Json.array(raw, "sample");
        if (pair.size() != 2) {
            throw new JsonException("expected [timestamp, value], got " + pair.size() + " elements");
        }
        double seconds = Json.number(pair.get(0), "sample timestamp");
        Instant timestamp;
        try {
            if (!Double.isFinite(seconds)) {
                throw new DateTimeException("not finite");
            }
            timestamp = Times.fromFloatSeconds(seconds);
        } catch (DateTimeException | ArithmeticException e) {
            throw new JsonException("bad sample timestamp " + seconds);
        }
        String text = Json.string(pair.get(1), "sample value");
        try {
            return new SamplePair(timestamp, parseValue(text));
        } catch (NumberFormatException e) {
            throw new JsonException("parsing sample value \"" + text + "\"");
        }
    }

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    /**
     * Parses a sample value the way Go's strconv.ParseFloat does: decimal numbers plus the special
     * values Inf, +Inf, -Inf, Infinity and NaN in any case.
     */
    static double parseValue(String s) {
        switch (s.toLowerCase(Locale.ROOT)) {
            case "inf", "+inf", "infinity", "+infinity":
                return Double.POSITIVE_INFINITY;
            case "-inf", "-infinity":
                return Double.NEGATIVE_INFINITY;
            case "nan":
                return Double.NaN;
            default:
                break;
        }
        // Double.parseDouble also takes forms Go rejects (a trailing 'd' or 'f', hex, whitespace).
        if (!DECIMAL.matcher(s).matches()) {
            throw new NumberFormatException("not a number: \"" + s + "\"");
        }
        return Double.parseDouble(s);
    }

    // ---------------------------------------------------------------- metadata

    /** A label set; JSON null or a missing object decodes to an empty map. */
    static Map<String, String> labels(Object raw) {
        Map<String, String> out = new TreeMap<>();
        Json.object(raw, "labels").forEach((k, v) -> out.put(k, Json.string(v, "label " + k)));
        return Collections.unmodifiableMap(out);
    }

    static List<Map<String, String>> labelSets(Object data) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Object item : Json.array(data, "series")) {
            out.add(labels(item));
        }
        return Collections.unmodifiableList(out);
    }

    static List<String> strings(Object data) {
        List<String> out = new ArrayList<>();
        for (Object item : Json.array(data, "list")) {
            out.add(Json.string(item, "list item"));
        }
        return Collections.unmodifiableList(out);
    }

    static List<Target> targets(Object data) {
        List<Target> out = new ArrayList<>();
        for (Object item : Json.array(data, "targets")) {
            Map<String, Object> t = Json.object(item, "target");
            out.add(new Target(
                    Json.string(t.get("job"), "job"),
                    Json.string(t.get("instance"), "instance"),
                    Json.string(t.get("scrapeUrl"), "scrapeUrl"),
                    Json.bool(t.get("up"), "up"),
                    instant(t.get("lastScrape"), "lastScrape"),
                    Json.string(t.get("lastError"), "lastError"),
                    Json.number(t.get("lastScrapeDuration"), "lastScrapeDuration"),
                    Json.integer(t.get("samplesScraped"), "samplesScraped")));
        }
        return Collections.unmodifiableList(out);
    }

    /** An RFC 3339 timestamp, or null when absent. */
    private static Instant instant(Object raw, String what) {
        if (raw == null) {
            return null;
        }
        String s = Json.string(raw, what);
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            throw new JsonException(what + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- push

    static PushResult pushResult(Object data) {
        Map<String, Object> r = Json.object(data, "push result");
        return new PushResult(
                Json.integer(r.get("samplesAppended"), "samplesAppended"),
                Json.integer(r.get("seriesTouched"), "seriesTouched"));
    }
}
