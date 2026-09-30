package dev.skirmish.fx;

/**
 * One effect particle: where it is and how it moves (blocks, blocks per second), how long it lives, and how its size
 * and colour change from birth to death. Mutable and pooled by {@link FxField}; builder-style setters return itself.
 */
public final class Fx {
    public FxShape shape = FxShape.GLOW;
    public double x;
    public double y;
    public double z;
    public double vx;
    public double vy;
    public double vz;
    /** Blocks per second² pulled down. */
    public float gravity;
    /** Fraction of speed kept per second (1 = none lost). */
    public float drag = 1f;
    public float age;
    public float life = 1f;
    public float size0 = 0.1f;
    public float size1 = 0f;
    public int color0 = 0xFFFFFFFF;
    public int color1 = 0x00FFFFFF;
    /** Rotation in the view plane (radians) and its speed; tumbling shapes also flip with it. */
    public float rot;
    public float spin;
    /** Streak length per block per second of speed (sparks). */
    public float stretch = 0.05f;

    Fx() {
    }

    public Fx at(double px, double py, double pz) {
        x = px;
        y = py;
        z = pz;
        return this;
    }

    public Fx vel(double sx, double sy, double sz) {
        vx = sx;
        vy = sy;
        vz = sz;
        return this;
    }

    public Fx life(float seconds) {
        life = Math.max(0.02f, seconds);
        return this;
    }

    public Fx size(float from, float to) {
        size0 = from;
        size1 = to;
        return this;
    }

    public Fx color(int from, int to) {
        color0 = from;
        color1 = to;
        return this;
    }

    /** Same colour fading to transparent. */
    public Fx fade(int argb) {
        color0 = argb;
        color1 = argb & 0x00FFFFFF;
        return this;
    }

    public Fx gravity(float g) {
        gravity = g;
        return this;
    }

    public Fx drag(float d) {
        drag = d;
        return this;
    }

    public Fx spin(float r, float s) {
        rot = r;
        spin = s;
        return this;
    }

    public Fx stretch(float s) {
        stretch = s;
        return this;
    }

    /** 0 at birth, 1 at death. */
    public float t() {
        return Math.min(1f, age / life);
    }

    public float size() {
        return size0 + (size1 - size0) * t();
    }

    public int color() {
        return FxColor.lerp(color0, color1, t());
    }

    void reset() {
        shape = FxShape.GLOW;
        x = y = z = vx = vy = vz = 0;
        gravity = 0;
        drag = 1f;
        age = 0;
        life = 1f;
        size0 = 0.1f;
        size1 = 0f;
        color0 = 0xFFFFFFFF;
        color1 = 0x00FFFFFF;
        rot = spin = 0;
        stretch = 0.05f;
    }
}
