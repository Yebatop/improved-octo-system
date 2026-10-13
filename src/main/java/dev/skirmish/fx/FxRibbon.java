package dev.skirmish.fx;

/**
 * A ribbon: a soft band through the points it was given, each point fading and thinning with age, so it trails
 * behind whatever lays it down (a comet's tail, a rainbow behind the feet, a swirling streamer). Points are pushed
 * from outside or by its {@link Mover} each frame until it stops; it ends when its last point has faded.
 */
public final class FxRibbon {
    /** Where the ribbon's head is at {@code age} seconds; writes x, y, z into {@code out}. */
    public interface Mover {
        void at(float age, double[] out);
    }

    private static final int CAP = 96;
    final double[] px = new double[CAP];
    final double[] py = new double[CAP];
    final double[] pz = new double[CAP];
    final float[] born = new float[CAP];
    final int[] col = new int[CAP];
    int start;
    int count;

    /** How long a point stays (seconds). */
    public float pointLife = 0.4f;
    public float width = 0.12f;
    public int color = 0xFFFFFFFF;
    public boolean add = true;
    public FxSprite sprite = FxSprite.RIBBON;
    /** Rainbow ribbons colour each point by the clock. */
    public boolean rainbow;
    /** A white-hot band down the middle at this fraction of the width (0 = none). */
    public float core;
    /** Stop laying points after this many seconds (the mover's ribbons). */
    public float stopAt = Float.MAX_VALUE;
    public Mover mover;
    float age;
    float clock;
    boolean stopped;
    private final double[] tmp = new double[3];

    /** Adds the head point now. */
    public void push(double x, double y, double z) {
        if (stopped) {
            return;
        }
        if (count > 0) {
            int last = (start + count - 1) % CAP;
            double dx = x - px[last];
            double dy = y - py[last];
            double dz = z - pz[last];
            // Points closer than a hair would only make the band fold.
            if (dx * dx + dy * dy + dz * dz < 0.0004) {
                px[last] = x;
                py[last] = y;
                pz[last] = z;
                return;
            }
        }
        if (count == CAP) {
            start = (start + 1) % CAP;
            count--;
        }
        int i = (start + count) % CAP;
        px[i] = x;
        py[i] = y;
        pz[i] = z;
        born[i] = clock;
        col[i] = rainbow ? FxColor.hsv(clock * 0.6f, 0.8f, 1f) : color;
        count++;
    }

    /** No more points; the ribbon fades out. */
    public void stop() {
        stopped = true;
    }

    void update(float dt) {
        clock += dt;
        age += dt;
        if (mover != null && !stopped) {
            if (age > stopAt) {
                stopped = true;
            } else {
                mover.at(age, tmp);
                push(tmp[0], tmp[1], tmp[2]);
            }
        }
        while (count > 0 && clock - born[start] > pointLife) {
            start = (start + 1) % CAP;
            count--;
        }
    }

    boolean done() {
        return stopped && count == 0;
    }

    void shift(double dx, double dz) {
        for (int k = 0; k < count; k++) {
            int i = (start + k) % CAP;
            px[i] += dx;
            pz[i] += dz;
        }
    }

    /** 0 for a fresh point, 1 for one about to vanish. */
    float ageOf(int k) {
        return Math.min(1f, (clock - born[(start + k) % CAP]) / pointLife);
    }
}
