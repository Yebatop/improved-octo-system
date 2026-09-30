package dev.skirmish.module.damagenumbers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * The floating numbers alive right now. A change on the same entity shortly after the previous one adds to that
 * number instead of stacking a new one (regeneration ticks, several hits in one tick); a crit or a totem pop reported
 * a moment after the health drop upgrades the number's color. Positions are in world coordinates; time is passed in
 * so tests can drive it. Pure Java, unit tested.
 */
final class NumberField {
    /** More numbers than this at once only clutter the screen; the oldest go first. */
    static final int MAX = 48;

    enum Kind {
        HIT, CRIT, TOTEM, HEAL;

        boolean heal() {
            return this == HEAL;
        }

        /** HIT < CRIT < TOTEM: a merged number keeps the most notable color. */
        Kind strongest(Kind other) {
            return other.ordinal() > ordinal() ? other : this;
        }
    }

    static final class Num {
        final int entityId;
        Kind kind;
        double amount;
        final double x;
        final double y;
        final double z;
        long bornMs;
        long updatedMs;

        Num(int entityId, Kind kind, double amount, double x, double y, double z, long now) {
            this.entityId = entityId;
            this.kind = kind;
            this.amount = amount;
            this.x = x;
            this.y = y;
            this.z = z;
            this.bornMs = now;
            this.updatedMs = now;
        }

        /** 0 at birth, 1 at the end of its life. */
        float age(long now, long lifetimeMs) {
            return lifetimeMs <= 0 ? 1f : Math.min(1f, Math.max(0f, (now - bornMs) / (float) lifetimeMs));
        }

        String text() {
            return (kind.heal() ? "+" : "") + format(amount);
        }
    }

    private final List<Num> nums = new ArrayList<>();

    /**
     * Adds a change of {@code amount} health points (positive) on an entity, or merges it into that entity's number of
     * the same sign when the previous change was at most {@code mergeMs} ago. A merge restarts the number's life
     * but keeps its place.
     */
    Num add(int entityId, Kind kind, double amount, double x, double y, double z, long now, long mergeMs) {
        for (int i = nums.size() - 1; i >= 0; i--) {
            Num n = nums.get(i);
            if (n.entityId == entityId && n.kind.heal() == kind.heal() && now - n.updatedMs <= mergeMs) {
                n.amount += amount;
                n.kind = n.kind.strongest(kind);
                n.updatedMs = now;
                n.bornMs = now;
                return n;
            }
        }
        Num n = new Num(entityId, kind, amount, x, y, z, now);
        nums.add(n);
        while (nums.size() > MAX) {
            nums.removeFirst();
        }
        return n;
    }

    /** A crit or totem reported just after the drop: recolors that entity's newest damage number within {@code windowMs}. */
    boolean upgrade(int entityId, Kind kind, long now, long windowMs) {
        for (int i = nums.size() - 1; i >= 0; i--) {
            Num n = nums.get(i);
            if (n.entityId == entityId && !n.kind.heal() && now - n.updatedMs <= windowMs) {
                n.kind = n.kind.strongest(kind);
                return true;
            }
        }
        return false;
    }

    void prune(long now, long lifetimeMs) {
        for (Iterator<Num> it = nums.iterator(); it.hasNext(); ) {
            if (now - it.next().bornMs >= lifetimeMs) {
                it.remove();
            }
        }
    }

    /** Removes every number of an entity (it turned invisible, or left). */
    void removeEntity(int entityId) {
        nums.removeIf(n -> n.entityId == entityId);
    }

    List<Num> live() {
        return nums;
    }

    boolean isEmpty() {
        return nums.isEmpty();
    }

    void clear() {
        nums.clear();
    }

    /** 4 → "4", 4.5 → "4.5", 0.25 → "0.3", 12.04 → "12": one decimal, dropped when it is zero. */
    static String format(double amount) {
        double rounded = Math.round(amount * 10) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }
}
