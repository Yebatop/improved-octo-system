package dev.skirmish.module.gearinspector.holy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Names and lore lines are the real ones from the HolyWorld wiki ("Кастомные предметы", "Зачарования", "Сферы и
 * Талисманы") and from public HolyWorld item parsers quoted in the research notes.
 */
class HolyItemsTest {
    // --- donor tiers ---

    @Test
    void tiersFromWikiAndParserNames() {
        assertEquals(DonorTier.GRIEFER, DonorTier.ofGearName("Шлем Griefer"));
        assertEquals(DonorTier.MUSTANG, DonorTier.ofGearName("Нагрудник Mustang"));
        assertEquals(DonorTier.GHAST, DonorTier.ofGearName("Ботинки Ghast"));
        assertEquals(DonorTier.WITHER, DonorTier.ofGearName("Штаны Wither"));
        assertEquals(DonorTier.KRAKEN, DonorTier.ofGearName("Нагрудник Kraken"));
        assertEquals(DonorTier.DRAGON, DonorTier.ofGearName("Шлем Dragon"));
        assertEquals(DonorTier.STINGER, DonorTier.ofGearName("Штаны стингер"));
        assertEquals(DonorTier.ETERNITY, DonorTier.ofGearName("Нагрудник этернити"));
        assertEquals(DonorTier.INFINITY, DonorTier.ofGearName("Шлем инфинити"));
        assertEquals(DonorTier.INFINITY, DonorTier.ofGearName("Поножи инфинити"));
        assertEquals(DonorTier.ETERNITY, DonorTier.ofGearName("Лопата ᴇᴛᴇʀɴɪᴛʏ"));
        assertEquals(DonorTier.ETERNITY, DonorTier.ofGearName("Арбалет этернити"));
        assertEquals(DonorTier.STINGER, DonorTier.ofGearName("Кирка стингер"));
        assertEquals(DonorTier.INFINITY, DonorTier.ofGearName("§dМеч §lInfinity"));
        assertEquals(DonorTier.SUN, DonorTier.ofGearName("Шлем солнца"));
        assertEquals(DonorTier.SUN, DonorTier.ofGearName("Ботинки солнца"));
        assertEquals(DonorTier.CERBERUS, DonorTier.ofGearName("Меч Цербера "));
    }

    @Test
    void nonGearAndVanillaNamesHaveNoTier() {
        assertNull(DonorTier.ofGearName("Сфера этернити"));
        assertNull(DonorTier.ofGearName("Талисман инфинити"));
        assertNull(DonorTier.ofGearName("- Рюкзак Iɴғɪɴɪᴛʏ -"));
        assertNull(DonorTier.ofGearName("Сфера Цербера"));
        assertNull(DonorTier.ofGearName("Меч Выгодный фарм"));
        assertNull(DonorTier.ofGearName("Незеритовый шлем"));
        assertNull(DonorTier.ofGearName("Броневая элитра"));
        assertNull(DonorTier.ofGearName(""));
        // Whole words only: "Гастрольный" does not contain the tier "гаст".
        assertNull(DonorTier.ofGearName("Гастрольный шлем"));
    }

    @Test
    void tiersAreOrderedAndGrouped() {
        assertEquals(DonorTier.Group.LOW, DonorTier.WITHER.group());
        assertEquals(DonorTier.Group.MID, DonorTier.KRAKEN.group());
        assertEquals(DonorTier.Group.HIGH, DonorTier.INFINITY.group());
        assertEquals("holy_tier_high", DonorTier.STINGER.group().colorToken());
        assertEquals(List.of(DonorTier.GRIEFER, DonorTier.MUSTANG, DonorTier.GHAST, DonorTier.WITHER, DonorTier.KRAKEN,
                DonorTier.DRAGON, DonorTier.STINGER, DonorTier.ETERNITY, DonorTier.INFINITY), List.of(DonorTier.values()).subList(0, 9));
    }

    @Test
    void summaryPicksTheMostWornTierHigherOnTies() {
        assertEquals(new TierSummary(DonorTier.INFINITY, 4), TierSummary.of(List.of(DonorTier.INFINITY, DonorTier.INFINITY,
                DonorTier.INFINITY, DonorTier.INFINITY)));
        assertEquals(new TierSummary(DonorTier.ETERNITY, 3), TierSummary.of(Arrays.asList(DonorTier.ETERNITY,
                DonorTier.INFINITY, DonorTier.ETERNITY, DonorTier.ETERNITY)));
        assertEquals(new TierSummary(DonorTier.STINGER, 1), TierSummary.of(Arrays.asList(null, DonorTier.KRAKEN, DonorTier.STINGER, null)));
        assertEquals(new TierSummary(DonorTier.SUN, 1), TierSummary.of(Arrays.asList(DonorTier.SUN, DonorTier.INFINITY, null, null)));
        assertNull(TierSummary.of(Arrays.asList(null, null, null, null)));
    }

    // --- custom enchantments ---

    @Test
    void customEnchantmentLoreLines() {
        assertEquals(new CustomEnchant.Found(CustomEnchant.IMPENETRABLE, 2), CustomEnchant.parseLine("Непробиваемый II"));
        assertEquals(new CustomEnchant.Found(CustomEnchant.IMPENETRABLE, 1), CustomEnchant.parseLine("§7Непробиваемый I"));
        assertEquals(new CustomEnchant.Found(CustomEnchant.DESTROYER, 3), CustomEnchant.parseLine("Разрушитель III"));
        assertEquals(new CustomEnchant.Found(CustomEnchant.CRITICAL, 2), CustomEnchant.parseLine("Критический II"));
        assertEquals(new CustomEnchant.Found(CustomEnchant.RICH, 6), CustomEnchant.parseLine("Богач VI"));
        assertEquals(new CustomEnchant.Found(CustomEnchant.STUN, 2), CustomEnchant.parseLine("Оглушение II"));
        assertEquals(new CustomEnchant.Found(CustomEnchant.HOMING, 4), CustomEnchant.parseLine("Самонаводка 4"));
        assertEquals(new CustomEnchant.Found(CustomEnchant.CRUSHER, 7), CustomEnchant.parseLine("Крушитель 7 ур."));
        assertEquals(new CustomEnchant.Found(CustomEnchant.LAVA_WALKER, 2), CustomEnchant.parseLine("• Лаваход II"));
        assertEquals(new CustomEnchant.Found(CustomEnchant.AUTO_SMELT, 1), CustomEnchant.parseLine("Автоплавка"));
        assertEquals(new CustomEnchant.Found(CustomEnchant.MEGA_DRILL, 1), CustomEnchant.parseLine("Мега-Бур I"));
        assertEquals(new CustomEnchant.Found(CustomEnchant.DRILL, 2), CustomEnchant.parseLine("Бур II"));
        assertEquals(new CustomEnchant.Found(CustomEnchant.EXPERIENCED, 3), CustomEnchant.parseLine("Опытный III"));
        assertEquals(new CustomEnchant.Found(CustomEnchant.MOB_FARMER, 13), CustomEnchant.parseLine("Фармер XIII"));
    }

    @Test
    void descriptionsAndOtherLoreAreNotEnchantments() {
        assertNull(CustomEnchant.parseLine("Особенности:"));
        assertNull(CustomEnchant.parseLine("Проклятие утраты"));
        assertNull(CustomEnchant.parseLine("• Урон II"));
        assertNull(CustomEnchant.parseLine("Ломает территорию вокруг вскопанного блока."));
        assertNull(CustomEnchant.parseLine("● Данный товар можно"));
        assertNull(CustomEnchant.parseLine("Непробиваемый броня"));
        assertNull(CustomEnchant.parseLine(""));
    }

    @Test
    void infinitySwordLore() {
        List<CustomEnchant.Found> found = CustomEnchant.parseLore(List.of("Богач VI", "Разрушитель II", "Критический II",
                "● Данный товар можно", "Богач I"));
        assertEquals(List.of(new CustomEnchant.Found(CustomEnchant.RICH, 6), new CustomEnchant.Found(CustomEnchant.DESTROYER, 2),
                new CustomEnchant.Found(CustomEnchant.CRITICAL, 2)), found);
        assertEquals("Разрушитель II", found.get(1).text());
        assertEquals("Автоплавка", new CustomEnchant.Found(CustomEnchant.AUTO_SMELT, 1).text());
    }

    // --- spheres and talismans ---

    @Test
    void infinityTalisman() {
        Talisman t = Talisman.parse("Талисман инфинити", List.of("• Макс. здоровье II", "• Броня II", "• Урон II", "• Скорость II"));
        assertNotNull(t);
        assertEquals(Talisman.Kind.TALISMAN, t.kind());
        assertEquals(List.of(new Talisman.Stat(Talisman.StatType.MAX_HEALTH, 2), new Talisman.Stat(Talisman.StatType.ARMOR, 2),
                new Talisman.Stat(Talisman.StatType.DAMAGE, 2), new Talisman.Stat(Talisman.StatType.SPEED, 2)), t.stats());
        assertNull(t.rune());
    }

    @Test
    void uniqueSpheres() {
        Talisman cerberus = Talisman.parse("Сфера Цербера", List.of("Проклятие утраты", "• Спешка I", "• Урон V"));
        assertNotNull(cerberus);
        assertEquals(Talisman.Kind.SPHERE, cerberus.kind());
        assertEquals(List.of(new Talisman.Stat(Talisman.StatType.HASTE, 1), new Talisman.Stat(Talisman.StatType.DAMAGE, 5)), cerberus.stats());

        Talisman armortality = Talisman.parse("Сфера ᴀʀᴍᴏʀᴛᴀʟɪᴛʏ", List.of("• Броня II", "• Макс. здоровье II", "• Урон II"));
        assertNotNull(armortality);
        assertEquals("Сфера ᴀʀᴍᴏʀᴛᴀʟɪᴛʏ", armortality.name());
        assertEquals(3, armortality.stats().size());

        Talisman attackSpeed = Talisman.parse("Сфера Сатиры", List.of("• Урон III", "• Скорость атаки 2"));
        assertNotNull(attackSpeed);
        assertEquals(new Talisman.Stat(Talisman.StatType.ATTACK_SPEED, 2), attackSpeed.stats().get(1));
    }

    @Test
    void statsWithoutAKnownNameStillMakeASphere() {
        Talisman t = Talisman.parse("Мифическая", List.of("• Скорость II", "• Броня III"));
        assertNotNull(t);
        assertEquals(Talisman.Kind.SPHERE, t.kind());
    }

    @Test
    void runeOnATotem() {
        Talisman t = Talisman.parse("Тотем бессмертия", List.of("Руна: Бессмертие"));
        assertNotNull(t);
        assertEquals(Talisman.Kind.TOTEM, t.kind());
        assertEquals(Talisman.Rune.IMMORTALITY, t.rune());
        assertEquals(Talisman.Rune.RESTORATION, Talisman.parse("Талисман", List.of("Эффект руны «Восстановление»")).rune());
    }

    @Test
    void plainItemsAreNotTalismans() {
        assertNull(Talisman.parse("", List.of()));
        assertNull(Talisman.parse("Тотем бессмертия", List.of("Особенности:", "Эффект руны")));
        assertNull(Talisman.parse("Щит", List.of("- нельзя поставить на землю;")));
    }
}
