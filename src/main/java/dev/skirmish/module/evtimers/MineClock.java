package dev.skirmish.module.evtimers;

import dev.skirmish.module.events.ServerParser;
import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When the auto-mine refills next. Two sources: the sidebar line HolyWorld shows at the mines ("… шахт… 4:12" or
 * "… 3 мин 5 сек"), which is exact, and refills seen as a burst of block updates, which teach the period (10 min
 * by default, shortened by the «Автошахта» investment). Between observations the countdown is extrapolated one
 * period at a time and marked as an estimate. Pure Java.
 */
final class MineClock {
    static final long DEFAULT_PERIOD_MS = 10 * 60_000L;
    private static final long MIN_PERIOD_MS = 60_000L;
    private static final long MAX_PERIOD_MS = 30 * 60_000L;

    private static final Pattern CLOCK = Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)");
    private static final Pattern MIN_SEC = Pattern.compile("(\\d{1,3})\\s*м(?:ин[а-я]*)?\\.?\\s*(\\d{1,2})\\s*с");
    private static final Pattern MIN = Pattern.compile("(\\d{1,3})\\s*м(?:ин[а-я]*)?\\.?(?![а-я])");
    private static final Pattern SEC = Pattern.compile("(\\d{1,4})\\s*с(?:ек[а-я]*)?\\.?(?![а-я])");

    private long period = DEFAULT_PERIOD_MS;
    private boolean learned;
    private long next = -1;
    private long lastRefill = -1;
    private long lastSidebar = -1;

    /**
     * Seconds until the refill named by a sidebar line, or -1: the line must be about the mine ("шахт") and hold a
     * time as "m:ss", "N мин M сек", "N мин" or "N сек".
     */
    static int parseSidebar(String line) {
        String text = ServerParser.normalize(line);
        if (!text.contains("шахт")) {
            return -1;
        }
        Matcher m = CLOCK.matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
        }
        m = MIN_SEC.matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
        }
        m = MIN.matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1)) * 60;
        }
        m = SEC.matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    void onSidebar(long now, int seconds) {
        next = now + seconds * 1000L;
        lastSidebar = now;
    }

    /** A refill seen as a block burst; two in a row within 1–30 min set the period. */
    void onRefill(long now) {
        if (lastRefill >= 0) {
            long gap = now - lastRefill;
            if (gap >= MIN_PERIOD_MS && gap <= MAX_PERIOD_MS) {
                period = gap;
                learned = true;
            }
        }
        lastRefill = now;
        next = now + period;
    }

    boolean known() {
        return next >= 0;
    }

    /** Time left; past the predicted refill the next period is assumed. */
    long remainingMs(long now) {
        if (next < 0) {
            return -1;
        }
        long at = next;
        while (at < now) {
            at += period;
        }
        return at - now;
    }

    /** The shown time is an extrapolation (no sidebar line or refill seen for the current period). */
    boolean estimate(long now) {
        if (next < 0) {
            return true;
        }
        boolean fresh = lastSidebar >= 0 && now - lastSidebar < 5_000L;
        return !fresh && now > next;
    }

    long periodMs() {
        return period;
    }

    boolean learnedPeriod() {
        return learned;
    }

    long lastSeen() {
        return Math.max(lastRefill, lastSidebar);
    }

    void reset() {
        period = DEFAULT_PERIOD_MS;
        learned = false;
        next = -1;
        lastRefill = -1;
        lastSidebar = -1;
    }

    /**
     * Block updates grouped into windows of up to {@code windowMs}: a window with at least {@code min} changed blocks
     * whose bounds fit the mine (21×9×21 plus slack) is a refill. Feed with {@link #add}, poll with {@link #poll}.
     */
    static final class Burst {
        private final int min;
        private final long windowMs;
        private final int maxXZ;
        private final int maxY;
        private long start = -1;
        private int count;
        private int minX, minY, minZ, maxX, maxYv, maxZ;

        Burst(int min, long windowMs, int maxXZ, int maxY) {
            this.min = min;
            this.windowMs = windowMs;
            this.maxXZ = maxXZ;
            this.maxY = maxY;
        }

        void add(long now, int x, int y, int z) {
            if (start < 0) {
                start = now;
                count = 0;
                minX = maxX = x;
                minY = maxYv = y;
                minZ = maxZ = z;
            }
            count++;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxYv = Math.max(maxYv, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        /** Closes a window older than {@code windowMs}; returns the centre {x, y, z} of a refill, or null. */
        int @Nullable [] poll(long now) {
            if (start < 0 || now - start < windowMs) {
                return null;
            }
            start = -1;
            boolean fits = maxX - minX < maxXZ && maxZ - minZ < maxXZ && maxYv - minY < maxY;
            if (count < min || !fits) {
                return null;
            }
            return new int[]{(minX + maxX) / 2, (minY + maxYv) / 2, (minZ + maxZ) / 2};
        }

        int count() {
            return start < 0 ? 0 : count;
        }
    }
}
