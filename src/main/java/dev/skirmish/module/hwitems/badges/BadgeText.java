package dev.skirmish.module.hwitems.badges;

import dev.skirmish.module.gearinspector.holy.Talisman;
import dev.skirmish.module.hwitems.HwItemInfo;
import dev.skirmish.module.hwitems.HwItemTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Which badges a slot gets and their texts. Pure Java (translations come in as a function), covered by tests.
 * <ul>
 *     <li>top left: TNT type ({@code A}, {@code B}, {@code C4}, {@code РВ}, {@code Б2}…), else backpack level, else the
 *     effects of a sphere or talisman ({@code У3 Б2});</li>
 *     <li>top right: the rune of a talisman or totem.</li>
 * </ul>
 */
public final class BadgeText {
    public static final String STAT_SHORT = "skirmish.hw_items.stat_short.";
    public static final String RUNE_SHORT = "skirmish.hw_items.rune_short.";
    public static final String BACKPACK = "skirmish.hw_items.badge.backpack";
    public static final String BACKPACK_INFINITY = "skirmish.hw_items.badge.backpack_infinity";

    public enum Kind {
        STATS("hwb_stats"),
        RUNE_IMMORTALITY("hwb_rune_immortality"),
        RUNE_RESTORATION("hwb_rune_restoration"),
        BACKPACK("hwb_backpack"),
        TNT("hwb_tnt");

        private final String color;

        Kind(String color) {
            this.color = color;
        }

        /** Theme colour token of the badge text. */
        public String color() {
            return color;
        }
    }

    public enum Corner {
        TOP_LEFT, TOP_RIGHT
    }

    /**
     * @param texts candidates from the most to the least detailed; the renderer draws the first that fits the slot
     */
    public record Badge(Kind kind, Corner corner, List<String> texts) {
        public Badge {
            texts = List.copyOf(texts);
        }
    }

    /** Which badge types are switched on. */
    public record Options(boolean stats, boolean runes, boolean backpacks, boolean tnt) {
        public static final Options ALL = new Options(true, true, true, true);
    }

    private BadgeText() {
    }

    public static List<Badge> badges(HwItemInfo info, Options options, Function<String, String> tr) {
        if (info.isEmpty()) {
            return List.of();
        }
        List<Badge> out = new ArrayList<>(2);
        HwItemTable.Entry entry = info.entry();
        if (options.tnt() && entry != null && entry.badge() && "tnt".equals(entry.group())) {
            out.add(new Badge(Kind.TNT, Corner.TOP_LEFT, List.of(tr.apply(entry.badgeKey()))));
        } else if (options.backpacks() && info.backpack() != 0) {
            if (info.backpack() > 0) {
                String text = info.backpack() == HwItemInfo.BACKPACK_INFINITY ? tr.apply(BACKPACK_INFINITY)
                        : String.format(Locale.ROOT, tr.apply(BACKPACK), info.backpack());
                out.add(new Badge(Kind.BACKPACK, Corner.TOP_LEFT, List.of(text)));
            }
        } else if (options.stats()) {
            List<String> texts = statTexts(info.stats(), tr);
            if (!texts.isEmpty()) {
                out.add(new Badge(Kind.STATS, Corner.TOP_LEFT, texts));
            }
        }
        Talisman.Rune rune = info.rune();
        if (options.runes() && rune != null) {
            Kind kind = rune == Talisman.Rune.IMMORTALITY ? Kind.RUNE_IMMORTALITY : Kind.RUNE_RESTORATION;
            out.add(new Badge(kind, Corner.TOP_RIGHT, List.of(tr.apply(RUNE_SHORT + rune.name().toLowerCase(Locale.ROOT)))));
        }
        return out;
    }

    /**
     * {@code У3 Б2}, then {@code У3Б2}; with more than three effects also the first three and a plus ({@code У2Б2С2+}).
     * Empty without effects.
     */
    static List<String> statTexts(Map<Talisman.StatType, Integer> stats, Function<String, String> tr) {
        if (stats.isEmpty()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>(stats.size());
        for (Map.Entry<Talisman.StatType, Integer> e : stats.entrySet()) {
            parts.add(tr.apply(STAT_SHORT + e.getKey().name().toLowerCase(Locale.ROOT)) + e.getValue());
        }
        List<String> texts = new ArrayList<>(3);
        texts.add(String.join(" ", parts));
        if (parts.size() > 1) {
            texts.add(String.join("", parts));
        }
        if (parts.size() > 3) {
            texts.add(String.join("", parts.subList(0, 3)) + "+");
        }
        return texts;
    }
}
