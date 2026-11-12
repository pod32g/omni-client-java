package io.github.pod32g.omni;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer, so the library needs nothing beyond the JDK (the Go original used
 * only its standard library too).
 *
 * <p>Parsed values are {@code Map<String, Object>}, {@code List<Object>}, {@code String},
 * {@code Double}, {@code Boolean} or {@code null}. The typed accessors follow Go's rule that JSON
 * null (or a missing field) decodes to the zero value: "", 0, false, an empty list or map.
 */
final class Json {
    private Json() {
    }

    /** Nesting limit, so a hostile body can't overflow the stack. */
    private static final int MAX_DEPTH = 1000;

    // ---------------------------------------------------------------- reading

    static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.value(0);
        p.skipWhitespace();
        if (p.pos != text.length()) {
            throw p.error("unexpected data after the value");
        }
        return value;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("nesting deeper than " + MAX_DEPTH);
            }
            if (pos >= s.length()) {
                throw error("unexpected end of input");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return object(depth);
                case '[':
                    return array(depth);
                case '"':
                    return string();
                case 't':
                    literal("true");
                    return Boolean.TRUE;
                case 'f':
                    literal("false");
                    return Boolean.FALSE;
                case 'n':
                    literal("null");
                    return null;
                default:
                    if (c == '-' || isDigit(c)) {
                        return number();
                    }
                    throw error("unexpected character '" + c + "'");
            }
        }

        private Map<String, Object> object(int depth) {
            pos++; // {
            Map<String, Object> out = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return out;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("expected a string key");
                }
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                out.put(key, value(depth + 1));
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                    continue;
                }
                expect('}');
                return out;
            }
        }

        private List<Object> array(int depth) {
            pos++; // [
            List<Object> out = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return out;
            }
            while (true) {
                skipWhitespace();
                out.add(value(depth + 1));
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                    continue;
                }
                expect(']');
                return out;
            }
        }

        private String string() {
            pos++; // opening quote
            StringBuilder out = new StringBuilder();
            while (true) {
                if (pos >= s.length()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c < 0x20) {
                    throw error("control character in string");
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= s.length()) {
                    throw error("unterminated escape");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"', '\\', '/' -> out.append(e);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) {
                            throw error("short \\u escape");
                        }
                        try {
                            out.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("bad \\u escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("bad escape '\\" + e + "'");
                }
            }
        }

        private Double number() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            if (peek() == '0') {
                pos++;
            } else if (isDigit(peek())) {
                digits();
            } else {
                throw error("bad number");
            }
            if (peek() == '.') {
                pos++;
                if (!isDigit(peek())) {
                    throw error("bad number");
                }
                digits();
            }
            if (peek() == 'e' || peek() == 'E') {
                pos++;
                if (peek() == '+' || peek() == '-') {
                    pos++;
                }
                if (!isDigit(peek())) {
                    throw error("bad number");
                }
                digits();
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        private void digits() {
            while (isDigit(peek())) {
                pos++;
            }
        }

        private void literal(String word) {
            if (!s.startsWith(word, pos)) {
                throw error("bad literal");
            }
            pos += word.length();
        }

        private void expect(char c) {
            if (peek() != c) {
                throw error("expected '" + c + "'");
            }
            pos++;
        }

        private char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    return;
                }
                pos++;
            }
        }

        JsonException error(String message) {
            return new JsonException("invalid JSON: " + message + " at offset " + pos);
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }

    // ---------------------------------------------------------------- typed access

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object v, String what) {
        if (v == null) {
            return Map.of();
        }
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw mismatch(what, "an object", v);
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object v, String what) {
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> l) {
            return (List<Object>) l;
        }
        throw mismatch(what, "an array", v);
    }

    static String string(Object v, String what) {
        if (v == null) {
            return "";
        }
        if (v instanceof String s) {
            return s;
        }
        throw mismatch(what, "a string", v);
    }

    static double number(Object v, String what) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Double d) {
            return d;
        }
        throw mismatch(what, "a number", v);
    }

    static int integer(Object v, String what) {
        double d = number(v, what);
        if (d != Math.rint(d) || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
            throw new JsonException(what + ": expected an integer, got " + d);
        }
        return (int) d;
    }

    static boolean bool(Object v, String what) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        throw mismatch(what, "a boolean", v);
    }

    private static JsonException mismatch(String what, String wanted, Object got) {
        String kind = switch (got) {
            case Map<?, ?> m -> "an object";
            case List<?> l -> "an array";
            case String s -> "a string";
            case Double d -> "a number";
            case Boolean b -> "a boolean";
            default -> got.getClass().getSimpleName();
        };
        return new JsonException(what + ": expected " + wanted + ", got " + kind);
    }

    // ---------------------------------------------------------------- writing

    /** Writes maps, lists, strings, numbers, booleans and null as JSON. */
    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    private static void write(StringBuilder out, Object v) {
        switch (v) {
            case null -> out.append("null");
            case String s -> writeString(out, s);
            case Boolean b -> out.append(b);
            case Double d -> writeDouble(out, d);
            case Integer i -> out.append(i);
            case Long l -> out.append(l);
            case Map<?, ?> m -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    writeString(out, String.valueOf(e.getKey()));
                    out.append(':');
                    write(out, e.getValue());
                }
                out.append('}');
            }
            case List<?> l -> {
                out.append('[');
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    write(out, l.get(i));
                }
                out.append(']');
            }
            default -> throw new IllegalArgumentException("can't write " + v.getClass().getName() + " as JSON");
        }
    }

    private static void writeDouble(StringBuilder out, double d) {
        if (!Double.isFinite(d)) {
            throw new IllegalArgumentException("JSON numbers can't be " + d);
        }
        // Whole numbers print without ".0" (1500, not 1500.0), as Go does.
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            out.append((long) d);
        } else {
            out.append(d);
        }
    }

    private static void writeString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
