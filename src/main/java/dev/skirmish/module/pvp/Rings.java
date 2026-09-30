package dev.skirmish.module.pvp;

import dev.skirmish.ui.Ui;

/** Countdown ring: a full track plus a clockwise arc from 12 o'clock, drawn with the UI kit's round-capped lines. */
final class Rings {
    private Rings() {
    }

    /**
     * @param diameter outer diameter in design px
     * @param width    band width
     * @param fraction arc share in [0, 1]
     */
    static void countdown(Ui ui, float cx, float cy, float diameter, float width, float fraction, int track, int arc) {
        ui.ring(cx, cy, diameter, width, track);
        float f = Math.max(0f, Math.min(1f, fraction));
        if (f <= 0f) {
            return;
        }
        float radius = (diameter - width) / 2f;
        int segments = Math.max(1, (int) Math.ceil(ui.num("layout.pvp.arc_segments") * f));
        float[] points = new float[(segments + 1) * 2];
        for (int i = 0; i <= segments; i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * f * i / segments;
            points[i * 2] = cx + (float) Math.cos(angle) * radius;
            points[i * 2 + 1] = cy + (float) Math.sin(angle) * radius;
        }
        ui.polyline(width, arc, points);
    }
}
