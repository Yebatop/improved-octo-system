package dev.skirmish.module.sky;

/** Sky maths for Custom Sky. Pure Java. */
final class SkyMath {
    private SkyMath() {
    }

    /**
     * How much of night it is at a time of day (0..24000 ticks, 6000 = noon): 0 by day, 1 in the deep night, with
     * dusk and dawn ramps.
     */
    static float night(long dayTime) {
        double t = Math.floorMod(dayTime, 24000L);
        double day = 0.5 + 0.5 * Math.cos(2 * Math.PI * (t - 6000) / 24000.0);
        return (float) Math.max(0.0, Math.min(1.0, (0.62 - day) / 0.4));
    }

    /** Direction (unit vector) for an azimuth and elevation in radians: y up, azimuth 0 = +Z. */
    static float[] dir(double azimuth, double elevation) {
        double c = Math.cos(elevation);
        return new float[]{(float) (c * Math.sin(azimuth)), (float) Math.sin(elevation), (float) (c * Math.cos(azimuth))};
    }

    static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static float[] normalize(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1e-6f ? new float[]{0, 1, 0} : new float[]{v[0] / l, v[1] / l, v[2] / l};
    }

    /** Two unit vectors perpendicular to {@code d} and to each other. */
    static float[][] basis(float[] d) {
        float[] up = Math.abs(d[1]) > 0.95f ? new float[]{1, 0, 0} : new float[]{0, 1, 0};
        float[] u = normalize(cross(d, up));
        float[] v = normalize(cross(u, d));
        return new float[][]{u, v};
    }

    static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }
}
