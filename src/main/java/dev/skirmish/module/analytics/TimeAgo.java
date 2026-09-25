package dev.skirmish.module.analytics;

/** "2 h ago": the largest whole unit of an age. Pure Java; the text comes from lang keys. */
public final class TimeAgo {
    public enum Unit {
        NOW, MINUTES, HOURS, DAYS
    }

    public record Value(Unit unit, long amount) {
    }

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private TimeAgo() {
    }

    public static Value of(long ageMs) {
        long age = Math.max(0, ageMs);
        if (age < MINUTE) {
            return new Value(Unit.NOW, 0);
        }
        if (age < HOUR) {
            return new Value(Unit.MINUTES, age / MINUTE);
        }
        if (age < DAY) {
            return new Value(Unit.HOURS, age / HOUR);
        }
        return new Value(Unit.DAYS, age / DAY);
    }
}
