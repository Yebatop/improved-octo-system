package dev.skirmish.fx;

import net.minecraft.util.RandomSource;

/**
 * A crackling electric arc between two points: a jagged line that redraws itself every few hundredths of a second,
 * with a side branch now and then, drawn as a glowing band with a white core.
 */
public final class FxBolt {
    static final int SEGMENTS = 9;
    double x0;
    double y0;
    double z0;
    double x1;
    double y1;
    double z1;
    float life;
    float age;
    float width;
    float jag;
    int color;
    final double[] pts = new double[(SEGMENTS + 1) * 3];
    final double[] branch = new double[5 * 3];
    boolean hasBranch;
    private float nextShape;
    private final RandomSource random = RandomSource.create();

    FxBolt() {
    }

    public FxBolt between(double ax, double ay, double az, double bx, double by, double bz) {
        x0 = ax;
        y0 = ay;
        z0 = az;
        x1 = bx;
        y1 = by;
        z1 = bz;
        reshape();
        return this;
    }

    public FxBolt look(float lifeSeconds, float bandWidth, float jaggedness, int argb) {
        life = lifeSeconds;
        width = bandWidth;
        jag = jaggedness;
        color = argb;
        return this;
    }

    void update(float dt) {
        age += dt;
        if (age >= nextShape) {
            reshape();
        }
    }

    private void reshape() {
        nextShape = age + 0.045f;
        double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
        double amp = len * jag;
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            double off = (i == 0 || i == SEGMENTS) ? 0 : 1;
            pts[i * 3] = x0 + (x1 - x0) * t + (random.nextDouble() - 0.5) * amp * off;
            pts[i * 3 + 1] = y0 + (y1 - y0) * t + (random.nextDouble() - 0.5) * amp * off;
            pts[i * 3 + 2] = z0 + (z1 - z0) * t + (random.nextDouble() - 0.5) * amp * off;
        }
        hasBranch = random.nextFloat() < 0.6f;
        if (hasBranch) {
            int from = 2 + random.nextInt(SEGMENTS - 4);
            double bx = pts[from * 3];
            double by = pts[from * 3 + 1];
            double bz = pts[from * 3 + 2];
            double dx = (random.nextDouble() - 0.5) * len * 0.5;
            double dy = (random.nextDouble() - 0.3) * len * 0.4;
            double dz = (random.nextDouble() - 0.5) * len * 0.5;
            for (int k = 0; k < 5; k++) {
                double t = k / 4.0;
                branch[k * 3] = bx + dx * t + (k == 0 ? 0 : (random.nextDouble() - 0.5) * amp * 0.5);
                branch[k * 3 + 1] = by + dy * t + (k == 0 ? 0 : (random.nextDouble() - 0.5) * amp * 0.5);
                branch[k * 3 + 2] = bz + dz * t + (k == 0 ? 0 : (random.nextDouble() - 0.5) * amp * 0.5);
            }
        }
    }

    /** Brightness now: on, a flicker, fading at the end. */
    float alpha() {
        float t = age / life;
        float flick = (int) (age * 40) % 3 == 0 ? 0.55f : 1f;
        return Math.max(0f, (1f - t * t)) * flick;
    }

    void shift(double dx, double dz) {
        x0 += dx;
        x1 += dx;
        z0 += dz;
        z1 += dz;
        for (int i = 0; i <= SEGMENTS; i++) {
            pts[i * 3] += dx;
            pts[i * 3 + 2] += dz;
        }
        for (int k = 0; k < 5; k++) {
            branch[k * 3] += dx;
            branch[k * 3 + 2] += dz;
        }
    }
}
