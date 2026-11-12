package io.github.pod32g.omni;

import static io.github.pod32g.omni.Assertions.assertEquals;
import static io.github.pod32g.omni.Assertions.assertThrows;
import static io.github.pod32g.omni.Assertions.assertTrue;

import java.util.List;

/** Port of value_test.go: decoding Prometheus' [timestamp, "value"] pairs. */
final class ValueTest {

    private static Decoders.SamplePair decode(String json) {
        return Decoders.samplePair(Json.parse(json));
    }

    @Test
    void samplePairDecodes() {
        record Case(String in, double wantValue, long wantSeconds) {
        }
        List<Case> cases = List.of(
                new Case("[1781538033.843, \"1\"]", 1, 1781538033L),
                new Case("[100, \"1.5\"]", 1.5, 100),
                new Case("[100, \"+Inf\"]", Double.POSITIVE_INFINITY, 100),
                new Case("[100, \"-Inf\"]", Double.NEGATIVE_INFINITY, 100),
                new Case("[100, \"-0.5\"]", -0.5, 100));
        for (Case c : cases) {
            Decoders.SamplePair s = decode(c.in());
            assertEquals(c.wantValue(), s.value());
            assertEquals(c.wantSeconds(), s.timestamp().getEpochSecond());
        }

        assertTrue(Double.isNaN(decode("[1, \"NaN\"]").value()), "NaN value not parsed");

        assertThrows(JsonException.class, () -> decode("[1]")); // a 1-element array
        assertThrows(JsonException.class, () -> decode("[1, 2]")); // a numeric (non-string) value
    }

    @Test
    void samplePairKeepsSubSecond() {
        assertEquals(500_000_000, decode("[1.5, \"1\"]").timestamp().getNano());
    }

    @Test
    void parseValueRejectsJavaOnlyForms() {
        // Double.parseDouble would accept all of these; Go's ParseFloat (and so the API) doesn't.
        for (String s : List.of("1d", "2f", " 3", "0x1p3", "")) {
            assertThrows(NumberFormatException.class, () -> Decoders.parseValue(s));
        }
    }
}
