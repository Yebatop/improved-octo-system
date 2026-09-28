package dev.skirmish.module.events;

import java.util.Map;

/**
 * How long HolyWorld events usually last, measured from the public API before release (appear to vanish, median),
 * so the panel can say how much is left before the client has seen an event of that kind end. Keys are the API's
 * event ids (Lite) and plugin names (Prime); values in ms. What the client learns itself takes over.
 */
final class EventLengths {
    static final Map<String, Long> LITE = Map.of();
    static final Map<String, Long> PRIME = Map.of();

    private EventLengths() {
    }
}
