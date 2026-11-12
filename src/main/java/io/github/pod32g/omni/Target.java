package io.github.pod32g.omni;

import java.time.Instant;

/**
 * The scrape health of one target, as reported by {@code /api/v1/targets}.
 *
 * @param lastScrape         when the target was last scraped, or null if the server didn't say
 * @param lastScrapeDuration how long the last scrape took, in seconds
 */
public record Target(
        String job,
        String instance,
        String scrapeUrl,
        boolean up,
        Instant lastScrape,
        String lastError,
        double lastScrapeDuration,
        int samplesScraped) {
}
