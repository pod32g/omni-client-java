package io.github.pod32g.omni;

import java.time.Instant;

/** A single (timestamp, value) pair within a range-vector series. */
public record Point(Instant timestamp, double value) {
}
