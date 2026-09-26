package dev.skirmish.module.hwtimers;

import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The item-timer table ({@code assets/skirmish_hwtimers/items.json}): which HolyWorld items put a timer on the local
 * player, for how long, and how each one is recognised. Pure Java, covered by tests.
 */
public final class TimerTable {
    public static final String RESOURCE = "/assets/skirmish_hwtimers/items.json";
    public static final String OVERRIDE_NAME = "hw_item_timers";

    /**
     * An own mob effect that marks the item: the effect first seen with an amplifier and a duration (in ticks) inside
     * these bounds.
     */
    public record EffectSig(String effectId, int minAmplifier, int maxAmplifier, int minTicks, int maxTicks) {
        public boolean matches(String id, int amplifier, int ticks) {
            return effectId.equals(id) && amplifier >= minAmplifier && amplifier <= maxAmplifier
                    && ticks >= minTicks && ticks <= maxTicks;
        }
    }

    /**
     * A burst of blocks appearing around me ({@code appear} ids, or {@code "*"} for any non-air block): at least
     * {@code min} within {@code radius} blocks during {@code windowMs}.
     */
    public record BlockSig(Set<String> appear, int min, double radius, long windowMs, boolean endsWhenGone) {
        public boolean anyBlock() {
            return appear.contains("*");
        }

        public boolean accepts(String blockId) {
            return anyBlock() || appear.contains(blockId);
        }
    }

    /** These blocks broken within {@code windowMs} after an explosion, no farther than {@code nearMe} from me. */
    public record BreakSig(Set<String> blocks, long windowMs, double nearMe) {
    }

    /**
     * One timer.
     *
     * @param seconds    countdown length; 0 for a status or an open-ended timer (counts up)
     * @param maxSeconds how long an open-ended timer may stay without an end signal
     * @param tone       theme colour of the chip's accent: bad, warn, good or accent
     * @param confirmed  the chat patterns were checked against a capture
     */
    public record TimerDef(String id, String mode, int seconds, int maxSeconds, String wiki, List<Pattern> chat,
                           @Nullable EffectSig effect, @Nullable String head, @Nullable BlockSig blocks,
                           @Nullable BreakSig breaks, @Nullable String totemRune, List<String> bossBar, String tone,
                           boolean confirmed) {
        public TimerDef {
            chat = List.copyOf(chat);
            bossBar = List.copyOf(bossBar);
        }

        public boolean countsDown() {
            return seconds > 0;
        }

        /** Whether a normalised server line mentions this timer. */
        public boolean matchesText(String normalized) {
            for (Pattern p : chat) {
                if (p.matcher(normalized).find()) {
                    return true;
                }
            }
            return false;
        }

        /** Whether a normalised boss-bar name is this status. */
        public boolean matchesBossBar(String normalized) {
            for (String part : bossBar) {
                if (!part.isEmpty() && normalized.contains(part)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final List<TimerDef> timers;
    private final List<String> problems;

    private TimerTable(List<TimerDef> timers, List<String> problems) {
        this.timers = List.copyOf(timers);
        this.problems = List.copyOf(problems);
    }

    public List<TimerDef> timers() {
        return timers;
    }

    /** Entries or patterns that were skipped while reading (for the debug log). */
    public List<String> problems() {
        return problems;
    }

    public @Nullable TimerDef byId(String id) {
        for (TimerDef def : timers) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

    /** Timers whose chat patterns match the normalised line, in table order. */
    public List<TimerDef> matchText(String normalized) {
        List<TimerDef> out = new ArrayList<>();
        for (TimerDef def : timers) {
            if (def.matchesText(normalized)) {
                out.add(def);
            }
        }
        return out;
    }

    /** The first timer whose effect signature matches a newly applied own effect. */
    public @Nullable TimerDef matchEffect(String effectId, int amplifier, int ticks) {
        for (TimerDef def : timers) {
            if (def.effect() != null && def.effect().matches(effectId, amplifier, ticks)) {
                return def;
            }
        }
        return null;
    }

    public static TimerTable bundled() {
        return parse(JsonTables.bundled(RESOURCE));
    }

    public static TimerTable parse(JsonObject root) {
        List<TimerDef> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (JsonObject o : JsonTables.objects(root, "timers")) {
            String id = JsonTables.string(o, "id", "").trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty() || !id.matches("[a-z0-9_]+")) {
                problems.add("timer without a valid id: " + o);
                continue;
            }
            if (out.stream().anyMatch(d -> d.id().equals(id))) {
                problems.add("duplicate timer id " + id);
                continue;
            }
            int seconds = (int) Math.max(0, JsonTables.number(o, "seconds", 0));
            int maxSeconds = (int) Math.max(0, JsonTables.number(o, "max_seconds", seconds > 0 ? seconds : 60));
            out.add(new TimerDef(id, JsonTables.string(o, "mode", "any"), seconds, maxSeconds,
                    JsonTables.string(o, "wiki", ""), JsonTables.patterns(JsonTables.strings(o, "chat"), id, problems),
                    effect(JsonTables.object(o, "effect")), blankToNull(JsonTables.string(o, "head", "")),
                    blocks(JsonTables.object(o, "blocks")), breaks(JsonTables.object(o, "explosion_breaks")),
                    blankToNull(JsonTables.string(o, "totem_rune", "")),
                    JsonTables.strings(o, "bossbar").stream().map(TimerText::normalize).toList(),
                    tone(JsonTables.string(o, "tone", "warn")), JsonTables.bool(o, "confirmed", false)));
        }
        return new TimerTable(out, problems);
    }

    private static @Nullable EffectSig effect(@Nullable JsonObject o) {
        if (o == null) {
            return null;
        }
        String id = JsonTables.string(o, "id", "");
        if (id.isEmpty()) {
            return null;
        }
        return new EffectSig(id.contains(":") ? id : "minecraft:" + id,
                (int) JsonTables.number(o, "min_amplifier", 0), (int) JsonTables.number(o, "max_amplifier", 255),
                (int) JsonTables.number(o, "min_ticks", 0), (int) JsonTables.number(o, "max_ticks", Integer.MAX_VALUE));
    }

    private static @Nullable BlockSig blocks(@Nullable JsonObject o) {
        if (o == null) {
            return null;
        }
        List<String> appear = JsonTables.strings(o, "appear");
        if (appear.isEmpty()) {
            return null;
        }
        return new BlockSig(Set.copyOf(appear), Math.max(1, (int) JsonTables.number(o, "min", 6)),
                JsonTables.number(o, "radius", 4), (long) JsonTables.number(o, "window_ms", 1500),
                JsonTables.bool(o, "ends_when_gone", true));
    }

    private static @Nullable BreakSig breaks(@Nullable JsonObject o) {
        if (o == null) {
            return null;
        }
        List<String> blocks = JsonTables.strings(o, "blocks");
        if (blocks.isEmpty()) {
            return null;
        }
        return new BreakSig(Set.copyOf(blocks), (long) JsonTables.number(o, "window_ms", 1500), JsonTables.number(o, "near_me", 24));
    }

    private static String tone(String raw) {
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "bad", "warn", "good", "accent" -> t;
            default -> "warn";
        };
    }

    private static @Nullable String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
