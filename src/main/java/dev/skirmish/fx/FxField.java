package dev.skirmish.fx;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything of one effects space: particles, ribbons, electric arcs, and effects waiting to start (later stages of
 * a burst). Advanced with real frame time so motion is smooth at any frame rate; capped so a flood of effects can't
 * slow the game.
 */
public final class FxField {
    private static final int MAX_RIBBONS = 48;
    private static final int MAX_BOLTS = 32;
    private final int max;
    private final List<Fx> live = new ArrayList<>();
    private final List<Fx> pool = new ArrayList<>();
    private final List<FxRibbon> ribbons = new ArrayList<>();
    private final List<FxBolt> bolts = new ArrayList<>();
    private final List<Delayed> delayed = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private float clock;

    private record Delayed(float at, Runnable action) {
    }

    public FxField(int max) {
        this.max = max;
    }

    /** A new particle of {@code sprite} (reusing a dead one); when full, the oldest particle is taken. */
    public Fx spawn(FxSprite sprite) {
        Fx fx;
        if (live.size() >= max) {
            fx = live.removeFirst();
        } else {
            fx = pool.isEmpty() ? new Fx() : pool.removeLast();
        }
        fx.reset();
        fx.sprite = sprite;
        fx.seed = random.nextInt();
        fx.swayPhase = random.nextFloat() * 6.283f;
        fx.frame0 = random.nextInt(Math.max(1, sprite.frames));
        live.add(fx);
        return fx;
    }

    public FxRibbon ribbon() {
        if (ribbons.size() >= MAX_RIBBONS) {
            ribbons.removeFirst();
        }
        FxRibbon r = new FxRibbon();
        ribbons.add(r);
        return r;
    }

    public FxBolt bolt() {
        if (bolts.size() >= MAX_BOLTS) {
            bolts.removeFirst();
        }
        FxBolt b = new FxBolt();
        bolts.add(b);
        return b;
    }

    /** Runs {@code action} (usually spawning) after {@code seconds}. */
    public void later(float seconds, Runnable action) {
        delayed.add(new Delayed(clock + seconds, action));
    }

    public void update(float dt) {
        dt = Math.max(0f, Math.min(0.1f, dt));
        clock += dt;
        if (!delayed.isEmpty()) {
            List<Delayed> due = new ArrayList<>();
            delayed.removeIf(d -> {
                if (d.at() <= clock) {
                    due.add(d);
                    return true;
                }
                return false;
            });
            due.forEach(d -> d.action().run());
        }
        List<Fx> landed = null;
        for (int i = live.size() - 1; i >= 0; i--) {
            Fx fx = live.get(i);
            fx.age += dt;
            if (fx.age >= fx.life) {
                live.remove(i);
                pool.add(fx);
                continue;
            }
            if (!fx.grounded) {
                fx.vy -= fx.gravity * dt;
            }
            if (fx.drag < 1f) {
                double keep = Math.pow(fx.drag, dt);
                fx.vx *= keep;
                fx.vy *= keep;
                fx.vz *= keep;
            }
            fx.y += fx.vy * dt;
            if (fx.orbitR > 0) {
                fx.ox += fx.vx * dt;
                fx.oz += fx.vz * dt;
                double a = fx.orbitAngle + fx.orbitSpeed * fx.age;
                fx.x = fx.ox + Math.cos(a) * fx.orbitR;
                fx.z = fx.oz + Math.sin(a) * fx.orbitR;
            } else {
                fx.x += fx.vx * dt;
                fx.z += fx.vz * dt;
            }
            if (fx.sway != 0) {
                double s = Math.cos(fx.swayPhase + fx.age * fx.swayFreq) * fx.sway * dt;
                fx.x += s;
                fx.z += Math.sin(fx.swayPhase * 1.7 + fx.age * fx.swayFreq * 0.8) * fx.sway * dt;
            }
            fx.rot += fx.spin * dt;
            if (fx.y < fx.floor) {
                if (fx.splat != 0) {
                    // A drop hitting the ground: gone, a splat left where it fell.
                    live.remove(i);
                    if (landed == null) {
                        landed = new ArrayList<>();
                    }
                    landed.add(fx);
                    continue;
                }
                fx.y = fx.floor;
                if (fx.bounce > 0 && fx.vy < -1.2) {
                    fx.vy = -fx.vy * fx.bounce;
                    fx.vx *= 0.7;
                    fx.vz *= 0.7;
                    fx.spin *= 0.6f;
                } else {
                    fx.vy = 0;
                    fx.grounded = true;
                }
            }
            if (fx.grounded) {
                // Sliding to a stop on the ground.
                double keep = Math.pow(0.02, dt);
                fx.vx *= keep;
                fx.vz *= keep;
                fx.spin *= (float) keep;
            }
        }
        if (landed != null) {
            for (Fx drop : landed) {
                float size = Math.max(drop.size0, drop.size1) * 2.2f;
                int c = drop.splat;
                spawn(FxSprite.SPLAT).at(drop.x, drop.floor + 0.012, drop.z).flat().painted().spin(random.nextFloat() * 6.28f, 0f)
                        .life(3f).size(size * 0.6f, size).grow(Fx.Grow.EASE).fadeIn(0.01f).ramp(c, c, c & 0x00FFFFFF);
                pool.add(drop);
            }
        }
        for (int i = ribbons.size() - 1; i >= 0; i--) {
            FxRibbon r = ribbons.get(i);
            r.update(dt);
            if (r.done()) {
                ribbons.remove(i);
            }
        }
        for (int i = bolts.size() - 1; i >= 0; i--) {
            FxBolt b = bolts.get(i);
            b.update(dt);
            if (b.age >= b.life) {
                bolts.remove(i);
            }
        }
    }

    /** Moves everything sideways (the Studio's treadmill under a running model). */
    public void shift(double dx, double dz) {
        for (Fx fx : live) {
            fx.x += dx;
            fx.z += dz;
            fx.ox += dx;
            fx.oz += dz;
        }
        for (FxRibbon r : ribbons) {
            r.shift(dx, dz);
        }
        for (FxBolt b : bolts) {
            b.shift(dx, dz);
        }
    }

    public List<Fx> live() {
        return live;
    }

    public List<FxRibbon> ribbons() {
        return ribbons;
    }

    public List<FxBolt> bolts() {
        return bolts;
    }

    public boolean isEmpty() {
        return live.isEmpty() && ribbons.isEmpty() && bolts.isEmpty() && delayed.isEmpty();
    }

    public void clear() {
        pool.addAll(live);
        live.clear();
        ribbons.clear();
        bolts.clear();
        delayed.clear();
    }

    public int size() {
        return live.size();
    }
}
