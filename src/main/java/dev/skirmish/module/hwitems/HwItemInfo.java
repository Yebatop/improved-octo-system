package dev.skirmish.module.hwitems;

import dev.skirmish.module.gearinspector.holy.DonorTier;
import dev.skirmish.module.gearinspector.holy.HolyText;
import dev.skirmish.module.gearinspector.holy.Talisman;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the mod knows about one HolyWorld item, derived only from its display name and lore (what the tooltip shows
 * anyway). Pure Java, covered by tests.
 *
 * @param entry     wiki table entry for the name, null when none
 * @param talisman  sphere/talisman/rune totem parsed from name and lore (gearinspector's parser), null otherwise
 * @param backpack  backpack level 1–4, {@link #BACKPACK_INFINITY} for «Рюкзак Infinity», 0 when not a backpack
 * @param tier      donor tier of an armour piece, null otherwise
 * @param armor     whether the item is worn in an armour slot
 */
public record HwItemInfo(HwItemTable.@Nullable Entry entry, @Nullable Talisman talisman, int backpack,
                         @Nullable DonorTier tier, boolean armor) {
    public static final int BACKPACK_INFINITY = 5;
    public static final HwItemInfo NONE = new HwItemInfo(null, null, 0, null, false);

    /**
     * @param name  custom name as displayed ("" for an item with its vanilla name)
     * @param lore  lore lines as displayed
     * @param armor the item goes into an armour slot
     */
    public static HwItemInfo of(String name, List<String> lore, boolean armor, HwItemTable table) {
        if (name.isEmpty() && lore.isEmpty()) {
            return armor ? new HwItemInfo(null, null, 0, null, true) : NONE;
        }
        HwItemTable.Entry entry = name.isEmpty() ? null : table.find(name);
        Talisman talisman = Talisman.parse(name, lore);
        int backpack = backpackLevel(name);
        DonorTier tier = armor && !name.isEmpty() ? DonorTier.ofGearName(name) : null;
        return new HwItemInfo(entry, talisman, backpack, tier, armor);
    }

    /**
     * {@code Рюкзак 2 уровня}, {@code Рюкзак II}, {@code - Рюкзак Iɴғɪɴɪᴛʏ -} → 2, 2, {@link #BACKPACK_INFINITY}; 0 for
     * anything that is not a backpack; -1 for a backpack whose level is not in the name.
     */
    public static int backpackLevel(String name) {
        List<String> words = HolyText.words(name);
        if (!words.contains("рюкзак") && !words.contains("backpack")) {
            return 0;
        }
        for (String word : words) {
            if (word.equals("infinity") || word.equals("инфинити")) {
                return BACKPACK_INFINITY;
            }
            int level = HolyText.level(word);
            if (level >= 1 && level <= 4) {
                return level;
            }
        }
        return -1;
    }

    /** Effects to show for a sphere or talisman: from the lore, or the wiki's fixed effects of a unique sphere. */
    public Map<Talisman.StatType, Integer> stats() {
        Map<Talisman.StatType, Integer> out = new EnumMap<>(Talisman.StatType.class);
        if (talisman != null && !talisman.stats().isEmpty()) {
            for (Talisman.Stat stat : talisman.stats()) {
                out.put(stat.type(), stat.level());
            }
        } else if (entry != null) {
            out.putAll(entry.stats());
        }
        return out;
    }

    public Talisman.@Nullable Rune rune() {
        return talisman == null ? null : talisman.rune();
    }

    /** Stinger, Eternity or Infinity armour: repairable with netherite ingots, drops ingots when broken. */
    public boolean topArmor() {
        return armor && (tier == DonorTier.STINGER || tier == DonorTier.ETERNITY || tier == DonorTier.INFINITY);
    }

    public boolean isEmpty() {
        return entry == null && talisman == null && backpack == 0 && !armor;
    }
}
