package io.github.pod32g.omni;

import static io.github.pod32g.omni.Assertions.assertEquals;
import static io.github.pod32g.omni.Assertions.assertNull;
import static io.github.pod32g.omni.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** New in the Java port: Go used encoding/json, Java has its own small Json class to test. */
final class JsonTest {

    @Test
    void parsesEveryKindOfValue() {
        Map<String, Object> v = Json.object(Json.parse(
                " {\"s\":\"x\",\"n\":-1.5e2,\"t\":true,\"f\":false,\"z\":null,\"a\":[1,[]],\"o\":{}} "), "test");
        assertEquals("x", v.get("s"));
        assertEquals(-150.0, v.get("n"));
        assertEquals(true, v.get("t"));
        assertEquals(false, v.get("f"));
        assertNull(v.get("z"));
        assertEquals(List.of(1.0, List.of()), v.get("a"));
        assertEquals(Map.of(), v.get("o"));
    }

    @Test
    void decodesEscapes() {
        assertEquals("a\"b\\c/d\n\té😀", Json.parse("\"a\\\"b\\\\c\\/d\\n\\t\\u00e9\\ud83d\\ude00\""));
    }

    @Test
    void rejectsMalformedInput() {
        for (String bad : List.of("", "{", "[1,]", "{\"a\" 1}", "01", "1.", "-", "tru", "\"open", "{} x", "\"tab\there\"")) {
            assertThrows(JsonException.class, () -> Json.parse(bad));
        }
    }

    @Test
    void limitsNesting() {
        char[] deep = new char[100_000];
        Arrays.fill(deep, '[');
        assertThrows(JsonException.class, () -> Json.parse(new String(deep)));
    }

    @Test
    void writesWhatItReads() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "quote\" slash\\ newline\n");
        m.put("whole", 1500.0);
        m.put("fraction", 0.25);
        m.put("ms", 1000L);
        m.put("list", List.of(true, "x"));
        m.put("nothing", null);
        String json = Json.write(m);
        assertEquals("{\"name\":\"quote\\\" slash\\\\ newline\\n\",\"whole\":1500,\"fraction\":0.25,"
                + "\"ms\":1000,\"list\":[true,\"x\"],\"nothing\":null}", json);
        assertEquals(json, Json.write(Json.parse(json))); // round trip
    }

    @Test
    void refusesNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.POSITIVE_INFINITY));
    }

    @Test
    void typedAccessorsTreatNullAsZero() {
        assertEquals("", Json.string(null, "s"));
        assertEquals(0.0, Json.number(null, "n"));
        assertEquals(List.of(), Json.array(null, "a"));
        assertThrows(JsonException.class, () -> Json.string(1.0, "s"));
        assertThrows(JsonException.class, () -> Json.integer(1.5, "i"));
    }
}
