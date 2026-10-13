package dev.skirmish.fx;

/**
 * One effect particle: a sprite from the effect atlas, where it is and how it moves (blocks, blocks per second), how
 * long it lives, how its size and colour change, how it faces (the camera, along its motion, flat on the ground or
 * in a given plane), whether it adds light or is painted over. Mutable and pooled by {@link FxField}; setters chain.
 */
public final class Fx {
    /** How a particle's quad is turned. */
    public enum Orient {
        /** Facing the camera. */
        BILLBOARD,
        /** Facing the camera, stretched along its motion (sparks, drops). */
        STRETCH,
        /** Lying flat on the ground. */
        FLAT,
        /** In the plane with normal {@code nx, ny, nz}. */
        PLANE
    }

    /** How size changes over life. */
    public enum Grow {
        /** Straight from size0 to size1. */
        LINEAR,
        /** Pops up to size0 quickly, then goes to size1. */
        POP,
        /** Eases out from size0 to size1 (shock waves). */
        EASE
    }

    public FxSprite sprite = FxSprite.GLOW;
    public Orient orient = Orient.BILLBOARD;
    public Grow grow = Grow.LINEAR;
    public boolean add = true;
    public double x;
    public double y;
    public double z;
    public double vx;
    public double vy;
    public double vz;
    public float nx;
    public float ny = 1f;
    public float nz;
    /** Blocks per second² pulled down. */
    public float gravity;
    /** Fraction of speed kept per second (1 = none lost). */
    public float drag = 1f;
    public float age;
    public float life = 1f;
    public float size0 = 0.1f;
    public float size1;
    /** Width over height of the quad. */
    public float aspect = 1f;
    public int color0 = 0xFFFFFFFF;
    public int colorMid;
    public int color1 = 0x00FFFFFF;
    /** Rotation in its plane (radians) and its speed. */
    public float rot;
    public float spin;
    /** Width wobble for tumbling things (0 = flat sprite, 1 = flips edge-on and back). */
    public float tumble;
    /** Streak length per block per second of speed (STRETCH). */
    public float stretch = 0.06f;
    /** A white-hot copy at this fraction of the size on top (0 = none). */
    public float core;
    /** Sideways sway (blocks per second) and its frequency. */
    public float sway;
    public float swayFreq = 3f;
    public float swayPhase;
    /** Random blinking strength, 0..1. */
    public float flicker;
    /** Part of life spent fading in. */
    public float fadeIn = 0.06f;
    /** Animated sprites: frames per second; 0 plays the frames once over life. */
    public float fps;
    public int frame0;
    /** Circling: radius and turn speed (radians per second) around {@code ox, oz}. */
    public float orbitR;
    public float orbitSpeed;
    public float orbitAngle;
    public double ox;
    public double oz;
    int seed;

    Fx() {
    }

    public Fx sprite(FxSprite s) {
        sprite = s;
        return this;
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

    public Fx grow(Grow g) {
        grow = g;
        return this;
    }

    public Fx color(int from, int to) {
        color0 = from;
        color1 = to;
        return this;
    }

    /** Three-stop colour over life. */
    public Fx ramp(int from, int mid, int to) {
        color0 = from;
        colorMid = mid;
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

    public Fx tumble(float t) {
        tumble = t;
        return this;
    }

    public Fx stretch(float s) {
        orient = Orient.STRETCH;
        stretch = s;
        return this;
    }

    public Fx flat() {
        orient = Orient.FLAT;
        return this;
    }

    /** In the plane facing direction {@code (dx, dy, dz)}. */
    public Fx facing(double dx, double dy, double dz) {
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-6) {
            return this;
        }
        orient = Orient.PLANE;
        nx = (float) (dx / l);
        ny = (float) (dy / l);
        nz = (float) (dz / l);
        return this;
    }

    public Fx aspect(float a) {
        aspect = a;
        return this;
    }

    public Fx painted() {
        add = false;
        return this;
    }

    public Fx core(float c) {
        core = c;
        return this;
    }

    public Fx sway(float amount, float freq) {
        sway = amount;
        swayFreq = freq;
        return this;
    }

    public Fx flicker(float f) {
        flicker = f;
        return this;
    }

    public Fx fadeIn(float f) {
        fadeIn = f;
        return this;
    }

    public Fx fps(float f) {
        fps = f;
        return this;
    }

    /** Circles around {@code (cx, cz)} at radius {@code r}, starting at {@code angle}, turning {@code speed} rad/s. */
    public Fx orbit(double cx, double cz, float r, float angle, float speed) {
        ox = cx;
        oz = cz;
        orbitR = r;
        orbitAngle = angle;
        orbitSpeed = speed;
        x = cx + Math.cos(angle) * r;
        z = cz + Math.sin(angle) * r;
        return this;
    }

    /** 0 at birth, 1 at death. */
    public float t() {
        return Math.min(1f, age / life);
    }

    public float size() {
        float t = t();
        return switch (grow) {
            case LINEAR -> size0 + (size1 - size0) * t;
            case POP -> t < 0.15f ? size0 * (t / 0.15f) : size0 + (size1 - size0) * ((t - 0.15f) / 0.85f);
            case EASE -> size0 + (size1 - size0) * (1 - (1 - t) * (1 - t) * (1 - t));
        };
    }

    public int color() {
        float t = t();
        int c;
        if (colorMid != 0) {
            c = t < 0.5f ? FxColor.lerp(color0, colorMid, t * 2) : FxColor.lerp(colorMid, color1, (t - 0.5f) * 2);
        } else {
            c = FxColor.lerp(color0, color1, t);
        }
        float a = 1f;
        if (fadeIn > 0 && t < fadeIn) {
            a = t / fadeIn;
        }
        if (flicker > 0) {
            int h = (int) (age * 30) * 73856093 ^ seed * 19349663;
            float noise = ((h >>> 8) & 0xFF) / 255f;
            a *= 1f - flicker + flicker * noise;
        }
        return a >= 1f ? c : FxColor.alpha(c, ((c >>> 24) / 255f) * a);
    }

    /** The animation frame to draw. */
    public int frame() {
        int n = sprite.frames;
        if (n <= 1) {
            return 0;
        }
        int f = fps > 0 ? (int) (age * fps) : (int) (t() * n);
        return Math.floorMod(f + frame0, n);
    }

    void reset() {
        sprite = FxSprite.GLOW;
        orient = Orient.BILLBOARD;
        grow = Grow.LINEAR;
        add = true;
        x = y = z = vx = vy = vz = 0;
        nx = nz = 0;
        ny = 1f;
        gravity = 0;
        drag = 1f;
        age = 0;
        life = 1f;
        size0 = 0.1f;
        size1 = 0f;
        aspect = 1f;
        color0 = 0xFFFFFFFF;
        colorMid = 0;
        color1 = 0x00FFFFFF;
        rot = spin = tumble = 0;
        stretch = 0.06f;
        core = 0;
        sway = 0;
        swayFreq = 3f;
        swayPhase = 0;
        flicker = 0;
        fadeIn = 0.06f;
        fps = 0;
        frame0 = 0;
        orbitR = orbitSpeed = orbitAngle = 0;
        ox = oz = 0;
    }
}
