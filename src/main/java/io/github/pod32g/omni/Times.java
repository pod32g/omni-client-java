package io.github.pod32g.omni;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Conversions between Java time types and the API's fractional Unix seconds. */
final class Times {
    private Times() {
    }

    /**
     * Renders an instant as fractional Unix seconds, the form the API parses, at full nanosecond
     * precision (e.g. {@code 100.123456}).
     */
    static String format(Instant t) {
        return decimalSeconds(t.getEpochSecond(), t.getNano());
    }

    /** Renders a duration as fractional seconds (e.g. {@code 15} or {@code 0.5}). */
    static String seconds(Duration d) {
        return decimalSeconds(d.getSeconds(), d.getNano());
    }

    private static String decimalSeconds(long seconds, int nanos) {
        return BigDecimal.valueOf(seconds)
                .add(BigDecimal.valueOf(nanos, 9))
                .stripTrailingZeros()
                .toPlainString();
    }

    /** Converts fractional Unix seconds to an instant, keeping sub-second precision. */
    static Instant fromFloatSeconds(double seconds) {
        long whole = (long) seconds; // truncates toward zero, like Go's math.Modf
        long nanos = Math.round((seconds - whole) * 1e9);
        return Instant.ofEpochSecond(whole, nanos);
    }
}
