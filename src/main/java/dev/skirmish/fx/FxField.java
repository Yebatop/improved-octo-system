package dev.skirmish.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of live effect particles plus effects waiting to start (the later stages of a kill burst). Advanced with
 * real frame time so motion is smooth at any frame rate; capped so a flood of effects can't slow the game.
 */
public final class FxField {
    private final int max;
    private final List<Fx> live = new ArrayList<>();
    private final List<Fx> pool = new ArrayList<>();
    private final List<Delayed> delayed = new ArrayList<>();
    private float clock;

    private record Delayed(float at, Runnable action) {
    }

    public FxField(int max) {
        this.max = max;
    }

    /** A new particle (reusing a dead one); when full, the oldest particle is taken. */
    public Fx spawn(FxShape shape) {
        Fx fx;
        if (live.size() >= max) {
            fx = live.removeFirst();
        } else {
            fx = pool.isEmpty() ? new Fx() : pool.removeLast();
        }
        fx.reset();
        fx.shape = shape;
        live.add(fx);
        return fx;
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
        for (int i = live.size() - 1; i >= 0; i--) {
            Fx fx = live.get(i);
            fx.age += dt;
            if (fx.age >= fx.life) {
                live.remove(i);
                pool.add(fx);
                continue;
            }
            fx.vy -= fx.gravity * dt;
            if (fx.drag < 1f) {
                double keep = Math.pow(fx.drag, dt);
                fx.vx *= keep;
                fx.vy *= keep;
                fx.vz *= keep;
            }
            fx.x += fx.vx * dt;
            fx.y += fx.vy * dt;
            fx.z += fx.vz * dt;
            fx.rot += fx.spin * dt;
        }
    }

    public List<Fx> live() {
        return live;
    }

    public boolean isEmpty() {
        return live.isEmpty() && delayed.isEmpty();
    }

    public void clear() {
        pool.addAll(live);
        live.clear();
        delayed.clear();
    }

    public int size() {
        return live.size();
    }
}
