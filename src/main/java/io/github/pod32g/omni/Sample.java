package io.github.pod32g.omni;

import java.time.Instant;
import java.util.Map;

/**
 * One element of an instant vector: a label set (including the reserved {@code __name__} label)
 * with a single value at an instant.
 */
public record Sample(Map<String, String> metric, Instant timestamp, double value) {
}
