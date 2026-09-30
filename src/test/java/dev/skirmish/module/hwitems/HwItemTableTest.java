package dev.skirmish.module.hwitems;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.module.gearinspector.holy.DonorTier;
import dev.skirmish.module.gearinspector.holy.Talisman;
import dev.skirmish.module.hwitems.badges.BadgeText;
import dev.skirmish.module.hwitems.tooltips.TooltipFacts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Name keys, table lookup, item info, badges and tooltip lines of the HolyWorld item modules. */
class HwItemTableTest {
    private static final HwItemTable TABLE = HwItemTable.get();

    private static JsonObject lang(String ns, String code) throws IOException {
        try (InputStream in = HwItemTableTest.class.getResourceAsStream("/assets/" + ns + "/lang/" + code + ".json")) {
            assertNotNull(in, ns + "/" + code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String id(String name) {
        HwItemTable.Entry e = TABLE.find(name);
        return e == null ? null : e.id();
    }

    @Test
    void keysNormaliseDecorationsAndHomoglyphs() {
        assertEquals("динамит a", HwItemKey.of("§6Динамит A"));
        assertEquals("динамит a", HwItemKey.of("Динамит А"));
        assertEquals("динамит b", HwItemKey.of("« Динамит В »"));
        assertEquals("c4 взрывчатка", HwItemKey.of("С4 Взрывчатка"));
        assertEquals("рюкзак infinity", HwItemKey.of("- Рюкзак Iɴғɪɴɪᴛʏ -"));
        assertEquals("тнт пушка", HwItemKey.of("Тнт-Пушка"));
        assertEquals("надежный стиллер", HwItemKey.of("Надёжный стиллер"));
        assertEquals("", HwItemKey.of("  ✦ "));
        assertTrue(HwItemKey.containsPhrase("мой стан 2", "стан"));
        assertFalse(HwItemKey.containsPhrase("станок", "стан"));
    }

    @Test
    void lookupPrefersExactThenLongestPhrase() {
        assertEquals("tnt_a", id("Динамит А"));
        assertEquals("tnt_b", id("§cДинамит B"));
        assertEquals("c4", id("С4 Взрывчатка"));
        assertEquals("tnt_b2", id("Динамит Б2"));
        assertEquals("stealer_reliable", id("✦ Надёжный стиллер ✦"));
        assertEquals("stealer", id("Стиллер"));
        assertEquals("trap_explosive", id("Взрывная трапка"));
        assertEquals("trap", id("Трапка"));
        assertEquals("stun", id("Стан"));
        assertEquals("sphere_armortality", id("Сфера ᴀʀᴍᴏʀᴛᴀʟɪᴛʏ"));
        assertEquals("sphere", id("Сфера урона"));
        assertEquals("talisman", id("Талисман Infinity"));
        assertEquals("egg_witch", id("Загадочное яйцо призыва ведьмы"));
        assertEquals("egg_mystery", id("Загадочное яйцо призыва"));
        assertEquals("backpack", id("Рюкзак 2 уровня"));
        assertEquals("rune_immortality", id("Руна \"Бессмертие\""));
        assertNull(id("Алмазный меч"));
        assertNull(id(""));
    }

    @Test
    void exactEntriesDoNotMatchInsideLongerNames() {
        assertEquals("keeper_hunter", id("Охотник"));
        assertNull(id("Меч охотник на драконов"));
        assertEquals("artifact", id("Артефакт"));
        assertNull(id("Сломанный артефакт"));
    }

    @Test
    void uniqueSpheresCarryTheirWikiEffects() {
        HwItemTable.Entry cerberus = TABLE.byId("sphere_cerberus");
        assertNotNull(cerberus);
        assertEquals(Map.of(Talisman.StatType.DAMAGE, 5, Talisman.StatType.ATTACK_SPEED, 1), cerberus.stats());
        assertEquals(4, TABLE.byId("sphere_infinity").stats().size());
    }

    @Test
    void parseRejectsDuplicates() {
        String dup = "{\"entries\":[{\"id\":\"a\",\"names\":[\"x\"]},{\"id\":\"b\",\"names\":[\"X\"]}]}";
        assertThrows(IllegalArgumentException.class, () -> HwItemTable.parse(new StringReader(dup)));
        String line = "{\"entries\":[{\"id\":\"a\",\"names\":[\"x\"],\"lines\":[\"one\",\"shared.two\"]}]}";
        assertEquals(List.of("skirmish.hw_items.fact.a.one", "skirmish.hw_items.fact.shared.two"),
                HwItemTable.parse(new StringReader(line)).byId("a").lines());
    }

    @Test
    void backpackLevels() {
        assertEquals(2, HwItemInfo.backpackLevel("Рюкзак 2 уровня"));
        assertEquals(3, HwItemInfo.backpackLevel("§bРюкзак III"));
        assertEquals(HwItemInfo.BACKPACK_INFINITY, HwItemInfo.backpackLevel("- Рюкзак Iɴғɪɴɪᴛʏ -"));
        assertEquals(-1, HwItemInfo.backpackLevel("Рюкзак"));
        assertEquals(0, HwItemInfo.backpackLevel("Шалкер"));
    }

    @Test
    void badgesForTalismanRuneTntBackpack() {
        Function<String, String> tr = key -> key.substring(key.lastIndexOf('.') + 1);
        HwItemInfo talisman = HwItemInfo.of("Талисман Infinity", List.of("• Урон III", "• Броня II", "Руна: Бессмертие"), false, TABLE);
        List<BadgeText.Badge> badges = BadgeText.badges(talisman, BadgeText.Options.ALL, tr);
        assertEquals(2, badges.size());
        assertEquals(BadgeText.Kind.STATS, badges.get(0).kind());
        assertEquals("damage3 armor2", badges.get(0).texts().getFirst());
        assertEquals("damage3armor2", badges.get(0).texts().get(1));
        assertEquals(BadgeText.Kind.RUNE_IMMORTALITY, badges.get(1).kind());
        assertEquals(BadgeText.Corner.TOP_RIGHT, badges.get(1).corner());

        HwItemInfo unique = HwItemInfo.of("Сфера Инфинити", List.of(), false, TABLE);
        List<String> texts = BadgeText.badges(unique, BadgeText.Options.ALL, tr).getFirst().texts();
        assertEquals(3, texts.size());
        assertTrue(texts.getLast().endsWith("+"), texts.toString());

        HwItemInfo c4 = HwItemInfo.of("С4 Взрывчатка", List.of(), false, TABLE);
        BadgeText.Badge tnt = BadgeText.badges(c4, BadgeText.Options.ALL, tr).getFirst();
        assertEquals(BadgeText.Kind.TNT, tnt.kind());
        assertEquals("c4", tnt.texts().getFirst());
        assertTrue(BadgeText.badges(c4, new BadgeText.Options(true, true, true, false), tr).isEmpty());

        Function<String, String> fmt = key -> key.endsWith("backpack") ? "ур%d" : key;
        HwItemInfo pack = HwItemInfo.of("Рюкзак 4 уровня", List.of(), false, TABLE);
        assertEquals("ур4", BadgeText.badges(pack, BadgeText.Options.ALL, fmt).getFirst().texts().getFirst());
        assertTrue(BadgeText.badges(HwItemInfo.of("Алмазный меч", List.of(), false, TABLE), BadgeText.Options.ALL, tr).isEmpty());
    }

    @Test
    void tooltipLinesIncludeRuneEffectsAndArmourRules() {
        HwItemInfo stun = HwItemInfo.of("Стан", List.of(), false, TABLE);
        List<String> keys = TooltipFacts.keys(stun, TABLE, true);
        assertEquals("skirmish.hw_items.fact.stun.cube", keys.getFirst());

        HwItemInfo totem = HwItemInfo.of("Тотем бессмертия", List.of("Руна: Восстановление"), false, TABLE);
        assertEquals(List.of("skirmish.hw_items.fact.rune_restoration.effect"), TooltipFacts.keys(totem, TABLE, true));

        HwItemInfo sphere = HwItemInfo.of("Сфера Цербера", List.of(), false, TABLE);
        List<String> sk = TooltipFacts.keys(sphere, TABLE, true);
        assertEquals(TooltipFacts.EFFECTS, sk.getFirst());
        List<String> texts = TooltipFacts.texts(sphere, sk, key -> key.equals(TooltipFacts.EFFECTS) ? "E: %s" : key.substring(key.lastIndexOf('.') + 1));
        assertEquals("E: damage 5, attack_speed 1", texts.getFirst());

        HwItemInfo chest = HwItemInfo.of("Нагрудник Eternity", List.of(), true, TABLE);
        assertEquals(DonorTier.ETERNITY, chest.tier());
        List<String> ck = TooltipFacts.keys(chest, TABLE, true);
        assertEquals(List.of("skirmish.hw_items.fact.armor.ingots", "skirmish.hw_items.fact.armor.drops",
                "skirmish.hw_items.fact.armor.unbreaking", "skirmish.hw_items.fact.armor.no_repair"), ck);
        assertEquals(2, TooltipFacts.keys(HwItemInfo.of("", List.of(), true, TABLE), TABLE, true).size());
        assertTrue(TooltipFacts.keys(HwItemInfo.of("", List.of(), true, TABLE), TABLE, false).isEmpty());
    }

    @Test
    void everyTableKeyIsTranslatedInBothLanguages() throws IOException {
        Set<String> ru = lang("skirmish_hw_tooltips", "ru_ru").keySet();
        assertEquals(lang("skirmish_hw_tooltips", "en_us").keySet(), ru);
        for (HwItemTable.Entry e : TABLE.entries()) {
            for (String key : e.lines()) {
                assertTrue(ru.contains(key), key);
            }
            if (e.badge()) {
                assertTrue(ru.contains(e.badgeKey()), e.badgeKey());
            }
            assertFalse(e.lines().isEmpty(), e.id());
        }
        for (Talisman.StatType t : Talisman.StatType.values()) {
            String n = t.name().toLowerCase(java.util.Locale.ROOT);
            assertTrue(ru.contains(BadgeText.STAT_SHORT + n), n);
            assertTrue(ru.contains(TooltipFacts.STAT + n), n);
        }
        for (Talisman.Rune r : Talisman.Rune.values()) {
            assertTrue(ru.contains(BadgeText.RUNE_SHORT + r.name().toLowerCase(java.util.Locale.ROOT)));
        }
        for (String k : List.of(BadgeText.BACKPACK, BadgeText.BACKPACK_INFINITY, TooltipFacts.EFFECTS,
                "skirmish.hw_items.tooltip.header", "skirmish.hw_items.tooltip.shift", "skirmish.hw_items.tooltip.bullet")) {
            assertTrue(ru.contains(k), k);
        }
        assertNotNull(TABLE.byId(HwItemTable.ARMOR));
        assertNotNull(TABLE.byId(HwItemTable.ARMOR_TOP));
    }
}
