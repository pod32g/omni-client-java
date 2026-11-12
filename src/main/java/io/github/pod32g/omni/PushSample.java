package io.github.pod32g.omni;

import java.time.Instant;

/**
 * One explicit observation. A null {@code timestamp} is omitted from the request, so the server
 * stamps it at receive time.
 */
public record PushSample(Instant timestamp, double value) {

    /** A sample the server stamps at receive time. */
    public static PushSample of(double value) {
        return new PushSample(null, value);
    }
}
