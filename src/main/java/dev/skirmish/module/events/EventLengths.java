package dev.skirmish.module.events;

import java.util.Map;

/**
 * How long HolyWorld events usually last, measured from the public API before release (appear to vanish, median),
 * so the panel can say how much is left before the client has seen an event of that kind end. Keys are the API's
 * event ids (Lite) and plugin names (Prime); values in ms. What the client learns itself takes over.
 */
final class EventLengths {
    /** Measured on 2026-09-28/29 over ~1.5 h of polls, kinds with at least three ended events. */
    static final Map<String, Long> LITE = Map.of(
            "CUBE", 1_164_000L,
            "JAYCOB", 1_068_000L,
            "CARGO", 786_000L,
            "SHIP", 786_000L,
            "GOLDEN_FORTRESS", 630_000L,
            "PARCELS", 2_040_000L);
    static final Map<String, Long> PRIME = Map.of(
            "bosses", 2_700_000L);

    private EventLengths() {
    }
}
