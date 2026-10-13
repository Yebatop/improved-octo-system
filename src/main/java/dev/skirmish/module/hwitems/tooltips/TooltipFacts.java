package dev.skirmish.module.hwitems.tooltips;

import dev.skirmish.module.gearinspector.holy.Talisman;
import dev.skirmish.module.hwitems.HwItemInfo;
import dev.skirmish.module.hwitems.HwItemTable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Translation keys (and ready texts) of the wiki lines for one item. Pure Java (translations come in as a function),
 * covered by tests.
 */
public final class TooltipFacts {
    public static final String EFFECTS = "skirmish.hw_items.effects";
    public static final String STAT = "skirmish.hw_items.stat.";
    static final String RUNE_IMMORTALITY = "rune_immortality";
    static final String RUNE_RESTORATION = "rune_restoration";

    private TooltipFacts() {
    }

    /**
     * Keys of the lines to show: the item's entry; the fixed effects of a unique sphere; its rune; the HolyWorld armour
     * rules for armour pieces when {@code armorFacts} is on. Duplicates are dropped, order kept.
     */
    public static List<String> keys(HwItemInfo info, HwItemTable table, boolean armorFacts) {
        Set<String> keys = new LinkedHashSet<>();
        HwItemTable.Entry entry = info.entry();
        if (entry != null) {
            if (!entry.stats().isEmpty() && (info.talisman() == null || info.talisman().stats().isEmpty())) {
                keys.add(EFFECTS);
            }
            keys.addAll(entry.lines());
        }
        Talisman.Rune rune = info.rune();
        if (rune != null) {
            HwItemTable.Entry runeEntry = table.byId(rune == Talisman.Rune.IMMORTALITY ? RUNE_IMMORTALITY : RUNE_RESTORATION);
            if (runeEntry != null && runeEntry != entry && !runeEntry.lines().isEmpty()) {
                keys.add(runeEntry.lines().getFirst());
            }
        }
        if (armorFacts && info.armor()) {
            if (info.topArmor()) {
                addLines(keys, table.byId(HwItemTable.ARMOR_TOP));
            }
            addLines(keys, table.byId(HwItemTable.ARMOR));
        }
        return List.copyOf(keys);
    }

    private static void addLines(Set<String> keys, HwItemTable.Entry entry) {
        if (entry != null) {
            keys.addAll(entry.lines());
        }
    }

    /**
     * The lines as text; {@link #EFFECTS} becomes {@code Эффекты: Урон 5, Скорость атаки 1} from the entry's fixed
     * effects ({@code format} fills the one {@code %s} of the pattern).
     */
    public static List<String> texts(HwItemInfo info, List<String> keys, Function<String, String> tr) {
        List<String> out = new ArrayList<>(keys.size());
        for (String key : keys) {
            if (key.equals(EFFECTS)) {
                out.add(String.format(Locale.ROOT, tr.apply(EFFECTS), effects(info.entry() == null ? Map.of() : info.entry().stats(), tr)));
            } else {
                out.add(tr.apply(key));
            }
        }
        return out;
    }

    static String effects(Map<Talisman.StatType, Integer> stats, Function<String, String> tr) {
        List<String> parts = new ArrayList<>(stats.size());
        for (Map.Entry<Talisman.StatType, Integer> e : stats.entrySet()) {
            parts.add(tr.apply(STAT + e.getKey().name().toLowerCase(Locale.ROOT)) + " " + e.getValue());
        }
        return String.join(", ", parts);
    }
}
