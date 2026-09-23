package dev.skirmish.module.killcam;

/** Playback speeds offered by the replay bar and the {@code default_speed} setting (mockup: 0,25× 0,5× 1× 2×). */
public enum ReplaySpeed {
    X025(0.25), X05(0.5), X1(1.0), X2(2.0);

    final double value;

    ReplaySpeed(double value) {
        this.value = value;
    }

    static ReplaySpeed nearest(double speed) {
        ReplaySpeed best = X1;
        for (ReplaySpeed s : values()) {
            if (Math.abs(s.value - speed) < Math.abs(best.value - speed)) {
                best = s;
            }
        }
        return best;
    }
}
