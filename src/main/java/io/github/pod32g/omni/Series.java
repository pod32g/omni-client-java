package io.github.pod32g.omni;

import java.util.List;
import java.util.Map;

/** One series of a range query: a label set with points over time. */
public record Series(Map<String, String> metric, List<Point> points) {
}
