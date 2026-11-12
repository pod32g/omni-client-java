package io.github.pod32g.omni;

import java.util.List;
import java.util.Map;

/**
 * One metric in a push: a name, optional extra labels, and either a single {@code value} (a sample
 * stamped at receive time) or explicit {@code samples}. Set exactly one: {@link OmniClient#push}
 * rejects a series with both or neither. The server injects job/instance and ignores any reserved
 * label ({@code __name__}, {@code job}, {@code instance}) supplied here.
 *
 * @param labels  may be null
 * @param value   null when {@code samples} is used
 * @param samples null or empty when {@code value} is used
 */
public record PushSeries(String name, Map<String, String> labels, Double value, List<PushSample> samples) {

    /** A series with a single value, stamped by the server at receive time. */
    public static PushSeries ofValue(String name, Map<String, String> labels, double value) {
        return new PushSeries(name, labels, value, null);
    }

    /** A series with explicit samples. */
    public static PushSeries ofSamples(String name, Map<String, String> labels, List<PushSample> samples) {
        return new PushSeries(name, labels, null, samples);
    }
}
