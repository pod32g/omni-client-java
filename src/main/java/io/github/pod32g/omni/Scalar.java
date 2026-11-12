package io.github.pod32g.omni;

import java.time.Instant;

/** A single numeric value at a timestamp. */
public record Scalar(Instant timestamp, double value) {
}
