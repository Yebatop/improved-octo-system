package dev.skirmish.module.gearinspector.holy;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HolyWorld Lite donor gear tiers, lowest first, as named by the wiki ("Кастомные предметы → Донатная броня",
 * "Улучшение предметов"): Griefer → Mustang → Ghast → Wither → Kraken → Dragon → Stinger → Eternity → Infinity, upgraded
 * in {@code /create}. {@link #SUN} (Шлем/Ботинки солнца, an upgraded Infinity helmet) and {@link #CERBERUS} (Меч
 * Цербера, an upgraded Infinity sword) are the unique items above Infinity. Pure Java, covered by tests.
 * <p>
 * Items are named {@code <piece> <tier>}: {@code Шлем Griefer}, {@code Нагрудник Kraken}, {@code Штаны Stinger},
 * {@code Меч инфинити}, {@code Кирка этернити}, {@code Лопата ᴇᴛᴇʀɴɪᴛʏ}. The tier word is matched as a whole word in
 * Latin or Cyrillic after {@link HolyText#normalize}.
 */
public enum DonorTier {
    GRIEFER("Griefer", Group.LOW, "griefer", "грифер", "гриффер"),
    MUSTANG("Mustang", Group.LOW, "mustang", "мустанг"),
    GHAST("Ghast", Group.LOW, "ghast", "гаст"),
    WITHER("Wither", Group.LOW, "wither", "визер"),
    KRAKEN("Kraken", Group.MID, "kraken", "кракен"),
    DRAGON("Dragon", Group.MID, "dragon", "драгон"),
    STINGER("Stinger", Group.HIGH, "stinger", "стингер"),
    ETERNITY("Eternity", Group.HIGH, "eternity", "этернити", "етернити"),
    INFINITY("Infinity", Group.HIGH, "infinity", "инфинити"),
    SUN("Солнца", Group.TOP, "солнца"),
    CERBERUS("Цербера", Group.TOP, "цербера");

    /** Colour group of the tier chip ({@code holy_tier_<group>} in theme/holyprofile.json). */
    public enum Group {
        LOW, MID, HIGH, TOP;

        public String colorToken() {
            return "holy_tier_" + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Words that make a name something other than worn gear even with a tier word in it: spheres and talismans
     * ({@code Сфера этернити}, {@code Талисман инфинити}), backpacks ({@code Рюкзак Infinity}), books, keys, kits.
     */
    private static final Set<String> NOT_GEAR = Set.of("сфера", "талисман", "рюкзак", "книга", "ключ", "кейс", "набор",
            "кит", "яйцо", "осколок", "фрагмент", "sphere", "talisman", "backpack", "book", "key");

    private final String display;
    private final Group group;
    private final List<String> aliases;

    DonorTier(String display, Group group, String... aliases) {
        this.display = display;
        this.group = group;
        this.aliases = List.of(aliases);
    }

    /** Name as HolyWorld writes it on the item ({@code Infinity}); the two unique items use the Russian word. */
    public String display() {
        return display;
    }

    public Group group() {
        return group;
    }

    public List<String> aliases() {
        return aliases;
    }

    /**
     * Tier of a worn or held piece of gear from its custom name, or null. Names of spheres, talismans, backpacks and
     * other non-gear items return null even when they carry a tier word.
     */
    public static @Nullable DonorTier ofGearName(String name) {
        List<String> words = HolyText.words(name);
        for (String word : words) {
            if (NOT_GEAR.contains(word)) {
                return null;
            }
        }
        for (String word : words) {
            for (DonorTier tier : values()) {
                if (tier.aliases.contains(word)) {
                    return tier;
                }
            }
        }
        return null;
    }
}
