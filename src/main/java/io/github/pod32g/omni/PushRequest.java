package io.github.pod32g.omni;

import java.util.List;

/**
 * One push of samples to {@code POST /api/v1/push}. Each push appends new samples (building a time
 * series), so {@code rate()} works on pushed counters.
 *
 * @param job      required
 * @param instance optional (null or empty); the server defaults it to the caller's remote host
 */
public record PushRequest(String job, String instance, List<PushSeries> series) {

    /** A push without an explicit instance. */
    public PushRequest(String job, List<PushSeries> series) {
        this(job, "", series);
    }
}
