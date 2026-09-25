package dev.skirmish.module.tnttimer;

import com.google.gson.JsonObject;
import dev.skirmish.module.hwtimers.JsonTables;
import dev.skirmish.module.hwtimers.TimerText;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * HolyWorld TNT types ({@code assets/skirmish_tnttimer/tnt.json}) and how a primed TNT is recognised: its custom name
 * first, then its block state, then a starting fuse that differs from vanilla's 80 ticks. Pure Java, covered by
 * tests.
 */
public final class TntTable {
    public static final String RESOURCE = "/assets/skirmish_tnttimer/tnt.json";
    public static final String OVERRIDE_NAME = "tnt_timer";
    /** How far a starting fuse may be from a type's {@code fuse_ticks} (the first sighting can be a few ticks late). */
    static final int FUSE_TOLERANCE_TICKS = 12;

    /**
     * One type.
     *
     * @param radius    explosion power the ring shows; 0 for types that do not blow up terrain; NaN when not given
     * @param cube      edge of a cubic blast in blocks, 0 when the blast is round
     * @param fuseTicks starting fuse when it differs from vanilla, else 0
     */
    public record TntType(String id, String mode, List<Pattern> match, Set<String> blocks, double radius, double cube,
                          int fuseTicks, String wiki) {
        public TntType {
            match = List.copyOf(match);
            blocks = Set.copyOf(blocks);
        }

        boolean matchesName(String normalized) {
            for (Pattern p : match) {
                if (p.matcher(normalized).find()) {
                    return true;
                }
            }
            return false;
        }
    }

    /** What the optional ring draws: a circle of {@code radius}, or a square of half-edge {@code radius}. */
    public record Ring(double radius, boolean square) {
    }

    private final int vanillaFuseTicks;
    private final double vanillaRadius;
    private final List<TntType> types;
    private final List<String> problems;

    private TntTable(int vanillaFuseTicks, double vanillaRadius, List<TntType> types, List<String> problems) {
        this.vanillaFuseTicks = vanillaFuseTicks;
        this.vanillaRadius = vanillaRadius;
        this.types = List.copyOf(types);
        this.problems = List.copyOf(problems);
    }

    public List<TntType> types() {
        return types;
    }

    public List<String> problems() {
        return problems;
    }

    public int vanillaFuseTicks() {
        return vanillaFuseTicks;
    }

    /**
     * The type of a primed TNT: by its normalised custom name (types in table order, so "надёжный стиллер" is tried
     * before "стиллер"), else by its block id, else by a starting fuse that is not vanilla's. Null for plain TNT or
     * when nothing is known.
     */
    public @Nullable TntType identify(String normalizedName, String blockId, int startFuseTicks) {
        if (!normalizedName.isBlank()) {
            for (TntType type : types) {
                if (type.matchesName(normalizedName)) {
                    return type;
                }
            }
        }
        for (TntType type : types) {
            if (type.blocks().contains(blockId)) {
                return type;
            }
        }
        if (startFuseTicks > vanillaFuseTicks + FUSE_TOLERANCE_TICKS / 2) {
            TntType best = null;
            for (TntType type : types) {
                if (type.fuseTicks() > 0 && Math.abs(type.fuseTicks() - startFuseTicks) <= FUSE_TOLERANCE_TICKS
                        && (best == null || Math.abs(type.fuseTicks() - startFuseTicks) < Math.abs(best.fuseTicks() - startFuseTicks))) {
                    best = type;
                }
            }
            return best;
        }
        return null;
    }

    /** The ring for a type (vanilla TNT when null); null when the type does not blow up terrain. */
    public @Nullable Ring ring(@Nullable TntType type) {
        if (type == null) {
            return vanillaRadius > 0 ? new Ring(vanillaRadius, false) : null;
        }
        if (type.cube() > 0) {
            return new Ring(type.cube() / 2.0, true);
        }
        double radius = Double.isNaN(type.radius()) ? vanillaRadius : type.radius();
        return radius > 0 ? new Ring(radius, false) : null;
    }

    public static TntTable bundled() {
        return parse(JsonTables.bundled(RESOURCE));
    }

    public static TntTable parse(JsonObject root) {
        List<String> problems = new ArrayList<>();
        List<TntType> types = new ArrayList<>();
        for (JsonObject o : JsonTables.objects(root, "types")) {
            String id = JsonTables.string(o, "id", "").trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty() || !id.matches("[a-z0-9_]+") || types.stream().anyMatch(t -> t.id().equals(id))) {
                problems.add("TNT type without a valid, unique id: " + o);
                continue;
            }
            types.add(new TntType(id, JsonTables.string(o, "mode", "any"),
                    JsonTables.patterns(JsonTables.strings(o, "match").stream().map(TntTable::normalizePattern).toList(), id, problems),
                    Set.copyOf(JsonTables.strings(o, "blocks")), JsonTables.number(o, "radius", Double.NaN),
                    Math.max(0, JsonTables.number(o, "cube", 0)), (int) Math.max(0, JsonTables.number(o, "fuse_ticks", 0)),
                    JsonTables.string(o, "wiki", "")));
        }
        return new TntTable((int) JsonTables.number(root, "vanilla_fuse_ticks", 80), JsonTables.number(root, "vanilla_radius", 4),
                types, problems);
    }

    /** Patterns are written like the names they match; lower-case them the way names are normalised. */
    private static String normalizePattern(String pattern) {
        return pattern.replace('ё', 'е');
    }

    /** Normalises a custom name the way the patterns expect. */
    public static String normalizeName(@Nullable String raw) {
        return raw == null ? "" : TimerText.normalize(raw);
    }
}
