package dev.skirmish.module.events;

import org.jspecify.annotations.Nullable;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * HolyWorld's published fixed schedule, all in Moscow time (UTC+3, no DST since 2014). Pure Java; the HUD converts
 * the returned instants to the user's own time zone.
 * <ul>
 *     <li>Захват Энда: every Sunday 16:00–17:30 MSK (all Lite anarchies);</li>
 *     <li>Бункер: spawn chance rolled every hour on the hour;</li>
 *     <li>event vote: "every 65 minutes" (may start), anchored to the last vote the client saw;</li>
 *     <li>daily restart: 04:30 MSK.</li>
 * </ul>
 */
public final class EventSchedule {
    public static final ZoneOffset MSK = ZoneOffset.ofHours(3);
    public static final Duration END_CAPTURE_LENGTH = Duration.ofMinutes(90);
    public static final LocalTime END_CAPTURE_START = LocalTime.of(16, 0);
    public static final LocalTime RESTART = LocalTime.of(4, 30);
    public static final Duration VOTE_PERIOD = Duration.ofMinutes(65);
    /** A vote anchor older than this is too uncertain to extrapolate (votes "may" start, the cycle drifts). */
    public static final Duration VOTE_ANCHOR_MAX_AGE = Duration.ofHours(4);

    /** A time span; {@code start == end} for instantaneous things (bunker roll, restart). */
    public record Window(Instant start, Instant end) {
        public boolean active(Instant now) {
            return !now.isBefore(start) && now.isBefore(end);
        }
    }

    private EventSchedule() {
    }

    /** The End capture running now, or the next one. */
    public static Window endCapture(Instant now) {
        ZonedDateTime msk = now.atZone(MSK);
        ZonedDateTime start = msk.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).with(END_CAPTURE_START);
        if (!now.isBefore(start.toInstant().plus(END_CAPTURE_LENGTH))) {
            start = start.plusWeeks(1);
        }
        return new Window(start.toInstant(), start.toInstant().plus(END_CAPTURE_LENGTH));
    }

    /** Next bunker roll: the next full hour (MSK is a whole-hour offset, so it is the next full UTC hour). */
    public static Instant nextBunker(Instant now) {
        return now.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS);
    }

    /** Next daily restart at 04:30 MSK (strictly after {@code now}). */
    public static Instant nextRestart(Instant now) {
        ZonedDateTime msk = now.atZone(MSK);
        ZonedDateTime today = msk.with(RESTART).truncatedTo(ChronoUnit.MINUTES);
        return (today.toInstant().isAfter(now) ? today : today.plusDays(1)).toInstant();
    }

    /**
     * Expected next vote: {@code anchor + k·65 min}, the first one not before {@code now}; null without an anchor
     * or when the anchor is older than {@link #VOTE_ANCHOR_MAX_AGE}.
     */
    public static @Nullable Instant nextVote(Instant now, @Nullable Instant anchor) {
        if (anchor == null) {
            return null;
        }
        if (anchor.isAfter(now)) {
            return anchor;
        }
        Duration age = Duration.between(anchor, now);
        if (age.compareTo(VOTE_ANCHOR_MAX_AGE) > 0) {
            return null;
        }
        long period = VOTE_PERIOD.toMillis();
        long cycles = (age.toMillis() + period - 1) / period;
        return anchor.plusMillis(cycles * period);
    }

    /** {@code h:mm:ss} from one hour, {@code m:ss} below; negative values count as zero. */
    public static String clock(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                : String.format(java.util.Locale.ROOT, "%d:%02d", m, sec);
    }
}
