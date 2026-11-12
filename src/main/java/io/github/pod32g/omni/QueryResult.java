package io.github.pod32g.omni;

import java.util.List;

/**
 * The typed result of a query. Exactly one of {@code vector}, {@code matrix} or {@code scalar} is
 * populated, per {@code type}; the other lists are empty and {@code scalar} is null unless the type
 * is {@link ResultType#SCALAR}.
 */
public record QueryResult(ResultType type, List<Sample> vector, List<Series> matrix, Scalar scalar) {
}
