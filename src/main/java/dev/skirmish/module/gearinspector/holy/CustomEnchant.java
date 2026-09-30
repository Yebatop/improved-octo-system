package dev.skirmish.module.gearinspector.holy;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HolyWorld Lite custom enchantments. They are not registry enchantments: the server writes them as lore lines such as
 * {@code Непробиваемый II}, {@code Разрушитель II}, {@code Автоплавка} (wiki "Зачарования → Список кастомных
 * зачарований"; line format from public HolyWorld item parsers). Pure Java, covered by tests.
 *
 * @param max highest level the wiki lists (sometimes exceeded by unique items, e.g. Разрушитель III on Меч Цербера)
 */
public enum CustomEnchant {
    // Armour
    IMPENETRABLE("Непробиваемый", 2, true, "непробиваемый", "impenetrable"),
    LAVA_WALKER("Лаваход", 2, false, "лаваход"),
    // Weapons
    CRUSHER("Крушитель", 10, false, "крушитель"),
    DESTROYER("Разрушитель", 3, true, "разрушитель", "destroyer"),
    CRITICAL("Критический", 2, true, "критический", "critical"),
    RICH("Богач", 6, false, "богач"),
    HOMING("Самонаводка", 4, true, "самонаводка"),
    STUN("Оглушение", 2, true, "оглушение"),
    MOB_FARMER("Фармер", 13, false, "фармер"),
    // Tools
    MAGNETISM("Магнетизм", 1, false, "магнетизм"),
    DRILL("Бур", 2, false, "бур"),
    MEGA_DRILL("Мега-бур", 1, false, "мега-бур", "мега бур", "мегабур"),
    AUTO_SMELT("Автоплавка", 1, false, "автоплавка"),
    EXPERIENCED("Опытный", 3, false, "опытный"),
    INDESTRUCTIBLE("Неразрушимость", 1, false, "неразрушимость"),
    LUMBERJACK("Дровосек", 3, false, "дровосек"),
    DELICATE("Деликатный", 3, false, "деликатный"),
    FARMER("Фермер", 5, false, "фермер"),
    SOWING("Посев", 4, false, "посев"),
    FILTER("Фильтр", 1, false, "фильтр");

    /** A recognised lore line. */
    public record Found(CustomEnchant enchant, int level) {
        /** {@code Непробиваемый II}; no numeral for single-level enchantments at level 1 ({@code Автоплавка}). */
        public String text() {
            return enchant.max == 1 && level == 1 ? enchant.display : enchant.display + " " + HolyText.roman(level);
        }
    }

    private static final Map<String, CustomEnchant> BY_NAME = new LinkedHashMap<>();

    static {
        for (CustomEnchant enchant : values()) {
            for (String alias : enchant.aliases) {
                BY_NAME.put(alias, enchant);
            }
        }
    }

    /** Leading decorations of lore lines: bullets, dashes, bars. */
    private static final String BULLETS = "•●○◆◇▪▫■□►▶▸-–—*·▍|>»";

    private final String display;
    private final int max;
    private final boolean combat;
    private final List<String> aliases;

    CustomEnchant(String display, int max, boolean combat, String... aliases) {
        this.display = display;
        this.max = max;
        this.combat = combat;
        this.aliases = List.of(aliases);
    }

    public String display() {
        return display;
    }

    public int max() {
        return max;
    }

    /** Matters in PvP (armour and weapon effects); farming and tool enchantments are false. */
    public boolean combat() {
        return combat;
    }

    /**
     * Parses one lore line. The whole line must be the enchantment name with an optional level ({@code II}, {@code 2},
     * {@code 2 ур.}), so description text that merely mentions a name ("…используется при добыче…") never matches.
     */
    public static @Nullable Found parseLine(String line) {
        String text = HolyText.normalize(line);
        int start = 0;
        while (start < text.length() && (BULLETS.indexOf(text.charAt(start)) >= 0 || text.charAt(start) == ' ')) {
            start++;
        }
        text = text.substring(start);
        for (String suffix : new String[]{" уровень", " уровня", " ур.", " ур", " lvl", " lv."}) {
            if (text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length()).trim();
                break;
            }
        }
        if (text.isEmpty() || text.length() > 40) {
            return null;
        }
        CustomEnchant whole = BY_NAME.get(text);
        if (whole != null) {
            return new Found(whole, 1);
        }
        int space = text.lastIndexOf(' ');
        if (space <= 0) {
            return null;
        }
        CustomEnchant named = BY_NAME.get(text.substring(0, space).trim());
        int level = HolyText.level(text.substring(space + 1));
        return named == null || level < 1 ? null : new Found(named, level);
    }

    /** Every custom enchantment in the lore, in lore order, each at most once (the first line wins). */
    public static List<Found> parseLore(List<String> lore) {
        List<Found> found = new ArrayList<>();
        for (String line : lore) {
            Found f = parseLine(line);
            if (f != null && found.stream().noneMatch(e -> e.enchant() == f.enchant())) {
                found.add(f);
            }
        }
        return found;
    }
}
