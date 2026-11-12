package io.github.pod32g.omni;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A minimal in-process metric store for a process that pushes instead of being scraped: accumulate
 * counters with {@link #add} and set gauges with {@link #set}, then hand {@link #series()} to a
 * {@link PushRequest}. It is safe for concurrent use.
 *
 * <p>A metric is identified by its name plus its label set, so the same name with different labels
 * is tracked as distinct series.
 */
public final class Registry {

    /** Separates the parts of a metric key; a character that never appears in real label text. */
    private static final char SEPARATOR = '\uFFFF';

    private final Map<String, Entry> metrics = new HashMap<>();

    private static final class Entry {
        final String name;
        final Map<String, String> labels; // immutable copy, or null when there are none
        double value;

        Entry(String name, Map<String, String> labels) {
            this.name = name;
            this.labels = labels;
        }
    }

    /**
     * Increments the counter identified by name and labels by {@code delta} (creating it at zero
     * first). Use for counters.
     */
    public synchronized void add(String name, Map<String, String> labels, double delta) {
        entry(name, labels).value += delta;
    }

    /** Assigns the value of the gauge identified by name and labels. Use for gauges. */
    public synchronized void set(String name, Map<String, String> labels, double value) {
        entry(name, labels).value = value;
    }

    /**
     * Returns an independent snapshot of every metric as {@link PushSeries}, sorted by name and
     * labels for deterministic output. The result is safe to hand to a push while the registry
     * keeps being updated.
     */
    public synchronized List<PushSeries> series() {
        List<String> keys = new ArrayList<>(metrics.keySet());
        Collections.sort(keys);
        List<PushSeries> out = new ArrayList<>(keys.size());
        for (String key : keys) {
            Entry e = metrics.get(key);
            out.add(PushSeries.ofValue(e.name, e.labels, e.value));
        }
        return Collections.unmodifiableList(out);
    }

    /** Finds or creates the entry for name+labels. Caller holds the lock. */
    private Entry entry(String name, Map<String, String> labels) {
        Objects.requireNonNull(name, "name");
        return metrics.computeIfAbsent(metricKey(name, labels), k -> new Entry(name, copyLabels(labels)));
    }

    /**
     * Returns an immutable copy of labels, or null when empty, so the caller's map and the
     * registry's can't change each other.
     */
    private static Map<String, String> copyLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableMap(new TreeMap<>(labels));
    }

    /** Builds a stable identity from a name and its (order-independent) labels. */
    private static String metricKey(String name, Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return name;
        }
        StringBuilder b = new StringBuilder(name);
        for (Map.Entry<String, String> label : new TreeMap<>(labels).entrySet()) {
            b.append(SEPARATOR).append(label.getKey()).append('=').append(label.getValue());
        }
        return b.toString();
    }
}
