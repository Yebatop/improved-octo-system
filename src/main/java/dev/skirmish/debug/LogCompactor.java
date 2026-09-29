package dev.skirmish.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps debug.log readable for hours instead of minutes. A line repeated by the same module (a server re-sending the
 * same effect every tick) is written once, then «↑ ещё N раз за X с» when something else comes or after
 * {@link #REPEAT_FLUSH_MS}; a module writing more than {@link #PER_MINUTE} lines a minute has the rest counted,
 * not written. Errors always pass. Pure Java; used by the log's writer thread only.
 */
final class LogCompactor {
    static final long REPEAT_FLUSH_MS = 30_000;
    static final int PER_MINUTE = 600;

    /** A line to write: when, which module, what. */
    record Line(long time, String tag, String message) {
    }

    private static final class TagState {
        String last;
        long lastAt;
        int repeats;
        long firstRepeatAt;
        long windowStart;
        int inWindow;
        int skipped;
    }

    private final Map<String, TagState> tags = new HashMap<>();

    /** A new line: what to write now (possibly nothing, possibly a summary before it). */
    List<Line> accept(long time, String tag, String message) {
        List<Line> out = new ArrayList<>(2);
        TagState s = tags.computeIfAbsent(tag, k -> new TagState());
        boolean error = message.startsWith("ERROR");
        if (!error && message.equals(s.last)) {
            if (s.repeats == 0) {
                s.firstRepeatAt = time;
            }
            s.repeats++;
            s.lastAt = time;
            return out;
        }
        summary(s, tag, time, out);
        if (time - s.windowStart >= 60_000) {
            if (s.skipped > 0) {
                out.add(new Line(time, tag, "… " + s.skipped + " строк за минуту не записано (слишком часто)"));
            }
            s.windowStart = time;
            s.inWindow = 0;
            s.skipped = 0;
        }
        s.last = message;
        s.lastAt = time;
        if (!error && ++s.inWindow > PER_MINUTE) {
            s.skipped++;
            return out;
        }
        out.add(new Line(time, tag, message));
        return out;
    }

    /** Summaries of repeats that went on long enough, and of skipped lines once their minute is over. */
    List<Line> tick(long now) {
        List<Line> out = new ArrayList<>();
        for (Map.Entry<String, TagState> e : tags.entrySet()) {
            TagState s = e.getValue();
            if (s.repeats > 0 && now - s.firstRepeatAt >= REPEAT_FLUSH_MS) {
                summary(s, e.getKey(), now, out);
            }
            if (s.skipped > 0 && now - s.windowStart >= 60_000) {
                out.add(new Line(now, e.getKey(), "… " + s.skipped + " строк за минуту не записано (слишком часто)"));
                s.skipped = 0;
                s.windowStart = now;
                s.inWindow = 0;
            }
        }
        return out;
    }

    private static void summary(TagState s, String tag, long time, List<Line> out) {
        if (s.repeats > 0) {
            long span = Math.max(0, s.lastAt - s.firstRepeatAt) / 1000;
            out.add(new Line(time, tag, "↑ ещё " + s.repeats + " раз за " + span + " с"));
            s.repeats = 0;
        }
    }
}
