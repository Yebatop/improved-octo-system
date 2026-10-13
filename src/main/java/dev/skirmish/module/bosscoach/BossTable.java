package dev.skirmish.module.bosscoach;

import com.google.gson.JsonObject;
import dev.skirmish.module.hwtimers.JsonTables;
import dev.skirmish.module.hwtimers.TimerText;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * HolyWorld bosses ({@code assets/skirmish_bosscoach/bosses.json}): which boss a boss bar belongs to, its health
 * and its phases by HP percent, as the wiki gives them. Pure Java, covered by tests.
 */
public final class BossTable {
    public static final String RESOURCE = "/assets/skirmish_bosscoach/bosses.json";
    public static final String OVERRIDE_NAME = "boss_coach";

    /** HP-percent bounds of a phase as the wiki writes them ("90–70%"), {@code from} ≥ {@code to}. */
    public record Phase(int from, int to) {
    }

    /**
     * One boss.
     *
     * @param hp    health from the wiki, 0 when the wiki gives none
     * @param top3  the wiki says the boss targets or rewards the top 3 by damage
     * @param pair  two bars that must be kept close (Крепкие орехи)
     */
    public record Boss(String id, String mode, List<String> names, List<String> exclude, int hp, List<Phase> phases,
                       boolean top3, boolean pair) {
        public Boss {
            names = List.copyOf(names);
            exclude = List.copyOf(exclude);
            phases = List.copyOf(phases);
        }

        /** Length of the longest alias the normalised text contains, or -1 (also when an excluded word is there). */
        int matchLength(String normalized) {
            for (String ex : exclude) {
                if (!ex.isEmpty() && normalized.contains(ex)) {
                    return -1;
                }
            }
            int best = -1;
            for (String name : names) {
                if (!name.isEmpty() && normalized.contains(name)) {
                    best = Math.max(best, name.length());
                }
            }
            return best;
        }
    }

    /**
     * Where the HP stands: {@code index} of the current phase (0-based), or -1 above the first described phase
     * (the fight has not reached it yet) or when the boss has no phases.
     */
    public record PhaseAt(int index, @Nullable Phase phase) {
        static final PhaseAt NONE = new PhaseAt(-1, null);
    }

    private final List<Boss> bosses;
    private final List<String> problems;

    private BossTable(List<Boss> bosses, List<String> problems) {
        this.bosses = List.copyOf(bosses);
        this.problems = List.copyOf(problems);
    }

    public List<Boss> bosses() {
        return bosses;
    }

    public List<String> problems() {
        return problems;
    }

    /** The boss whose longest alias the bar name contains (so "ледяной тролль" beats a shorter alias elsewhere). */
    public @Nullable Boss match(String rawBarName) {
        String normalized = TimerText.normalize(rawBarName);
        Boss best = null;
        int bestLength = -1;
        for (Boss boss : bosses) {
            int length = boss.matchLength(normalized);
            if (length > bestLength) {
                best = boss;
                bestLength = length;
            }
        }
        return best;
    }

    /**
     * The phase at {@code percent} HP: the phase with the highest lower bound not above {@code percent}; above the
     * first phase's upper bound the fight is before phase 1 ({@link PhaseAt#index()} -1 with that first phase as
     * {@code phase}, so the HUD can say where it starts).
     */
    public static PhaseAt phaseAt(Boss boss, double percent) {
        List<Phase> phases = boss.phases();
        if (phases.isEmpty()) {
            return PhaseAt.NONE;
        }
        Phase first = phases.getFirst();
        if (percent > first.from() + 0.5) {
            return new PhaseAt(-1, first);
        }
        for (int i = 0; i < phases.size(); i++) {
            Phase p = phases.get(i);
            if (percent >= p.to()) {
                return new PhaseAt(i, p);
            }
        }
        return new PhaseAt(phases.size() - 1, phases.getLast());
    }

    public static BossTable bundled() {
        return parse(JsonTables.bundled(RESOURCE));
    }

    public static BossTable parse(JsonObject root) {
        List<Boss> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (JsonObject o : JsonTables.objects(root, "bosses")) {
            String id = JsonTables.string(o, "id", "").trim().toLowerCase(Locale.ROOT);
            List<String> names = JsonTables.strings(o, "names").stream().map(TimerText::normalize).filter(n -> !n.isEmpty()).toList();
            if (id.isEmpty() || !id.matches("[a-z0-9_]+") || names.isEmpty() || out.stream().anyMatch(b -> b.id().equals(id))) {
                problems.add("boss without a valid, unique id or names: " + o);
                continue;
            }
            List<Phase> phases = new ArrayList<>();
            for (JsonObject p : JsonTables.objects(o, "phases")) {
                int from = (int) JsonTables.number(p, "from", -1);
                int to = (int) JsonTables.number(p, "to", -1);
                if (from < 0 || to < 0 || from > 100 || to > from) {
                    problems.add(id + ": bad phase " + p);
                    continue;
                }
                phases.add(new Phase(from, to));
            }
            phases.sort(Comparator.comparingInt(Phase::from).reversed());
            out.add(new Boss(id, JsonTables.string(o, "mode", "any"), names,
                    JsonTables.strings(o, "exclude").stream().map(TimerText::normalize).toList(),
                    (int) Math.max(0, JsonTables.number(o, "hp", 0)), phases, JsonTables.bool(o, "top3", false),
                    JsonTables.bool(o, "pair", false)));
        }
        return new BossTable(out, problems);
    }
}
