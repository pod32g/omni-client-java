package io.github.pod32g.omni;

import static io.github.pod32g.omni.Assertions.assertEquals;
import static io.github.pod32g.omni.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Port of registry_test.go. */
final class RegistryTest {

    /**
     * Returns the value of the series with this name and (optional) single label, or null if
     * absent. Pass a null labelKey for "no labels".
     */
    private static Double findSeries(List<PushSeries> series, String name, String labelKey, String labelValue) {
        for (PushSeries s : series) {
            if (!s.name().equals(name)) {
                continue;
            }
            boolean noLabels = s.labels() == null || s.labels().isEmpty();
            if (labelKey == null) {
                if (noLabels) {
                    return s.value();
                }
                continue;
            }
            if (!noLabels && labelValue.equals(s.labels().get(labelKey))) {
                return s.value();
            }
        }
        return null;
    }

    /** The value of a series that must exist. */
    private static double valueOf(List<PushSeries> series, String name, String labelKey, String labelValue) {
        Double v = findSeries(series, name, labelKey, labelValue);
        assertNotNull(v);
        return v;
    }

    @Test
    void addAndSet() {
        Registry r = new Registry();
        r.add("hits_total", null, 1);
        r.add("hits_total", null, 2); // accumulates
        r.set("temp", Map.of("room", "a"), 21.5);
        r.set("temp", Map.of("room", "a"), 22.0); // overwrites
        r.add("hits_total", Map.of("path", "/x"), 5);

        List<PushSeries> series = r.series();
        assertEquals(3, series.size());
        assertEquals(3.0, valueOf(series, "hits_total", null, null));
        assertEquals(22.0, valueOf(series, "temp", "room", "a"));
        assertEquals(5.0, valueOf(series, "hits_total", "path", "/x"));
    }

    @Test
    void seriesIsIndependentSnapshot() {
        Registry r = new Registry();
        r.set("g", null, 1);
        List<PushSeries> series = r.series();
        r.set("g", null, 99); // mutate after the snapshot
        assertEquals(1.0, valueOf(series, "g", null, null));
    }

    @Test
    void labelCopyIsolation() {
        Registry r = new Registry();
        Map<String, String> labels = new HashMap<>(Map.of("k", "v1"));
        r.set("g", labels, 1);
        labels.put("k", "v2"); // the caller changes their map after the call
        assertNotNull(findSeries(r.series(), "g", "k", "v1"));
    }

    @Test
    void concurrentAdds() throws InterruptedException {
        Registry r = new Registry();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            threads.add(Thread.startVirtualThread(() -> r.add("c", null, 1)));
        }
        for (Thread t : threads) {
            t.join();
        }
        assertEquals(100.0, valueOf(r.series(), "c", null, null));
    }
}
