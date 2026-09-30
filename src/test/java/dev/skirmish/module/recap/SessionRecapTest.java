package dev.skirmish.module.recap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.hwitems.badges.TalismanBadgesModule;
import dev.skirmish.module.hwitems.tooltips.HwTooltipsModule;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.Setting;
import dev.skirmish.ui.Theme;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recap aggregation, formats, balance lines, the PNG card, and module metadata of the three hw-utility modules. */
class SessionRecapTest {
    private static RecapFight fight(String name, float damage, boolean known, int hits, int totems, RecapFight.Result result) {
        return new RecapFight(name, null, damage, known, hits, 2, totems, 42_000, result);
    }

    @Test
    void trackerAddsUpASession() {
        RecapTracker t = new RecapTracker();
        t.kill();
        assertFalse(t.active());
        t.begin(1_000, "Me", "mc.holyworld.ru");
        t.kill();
        t.kill();
        t.death();
        t.myTotem();
        t.fightEnded(fight("A", 10f, true, 5, 1, RecapFight.Result.WIN), true);
        t.fightEnded(fight("B", 25f, true, 4, 0, RecapFight.Result.LOSS), true);
        t.fightEnded(fight("Hidden", 99f, true, 30, 3, RecapFight.Result.WIN), false);
        t.fightEnded(fight("Unknown", 0f, false, 50, 0, RecapFight.Result.OTHER), true);
        t.balance(1_000);
        t.balance(1_500);
        t.level(30);
        t.level(28);
        SessionRecap live = t.snapshot(61_000, false);
        assertEquals(2, live.kills());
        assertEquals(1, live.deaths());
        assertEquals(2.0, live.kd());
        assertEquals(4, live.fights());
        assertEquals(2, live.wins());
        assertEquals(4, live.totemsPopped());
        assertEquals(1, live.myTotems());
        assertEquals("B", live.best().opponent(), "known damage wins, the hidden opponent is never named");
        assertEquals(500L, live.coins());
        assertEquals(-2, live.levels());
        assertEquals(60_000, live.playtimeMs());
        assertFalse(live.finished());

        SessionRecap end = t.end(121_000);
        assertNotNull(end);
        assertTrue(end.finished());
        assertFalse(t.active());
        assertNull(t.end(130_000));
        t.kill();
        assertEquals(2, end.kills());
    }

    @Test
    void newSessionStartsFromZero() {
        RecapTracker t = new RecapTracker();
        t.begin(0, "Me", "s");
        t.kill();
        t.end(10);
        t.begin(20, "Me", "s");
        SessionRecap r = t.snapshot(30, false);
        assertEquals(0, r.kills());
        assertNull(r.best());
        assertNull(r.coins());
        assertNull(r.levels());
        assertTrue(r.empty());
        assertEquals(0, r.kd());
    }

    @Test
    void bestFightOrdering() {
        RecapFight a = fight("A", 10f, false, 8, 0, RecapFight.Result.OTHER);
        RecapFight b = fight("B", 10f, false, 6, 0, RecapFight.Result.OTHER);
        assertTrue(a.betterThan(b));
        assertFalse(b.betterThan(a));
        assertTrue(fight("C", 1f, true, 1, 0, RecapFight.Result.OTHER).betterThan(a));
        assertTrue(fight("D", 5f, true, 3, 2, RecapFight.Result.OTHER).betterThan(fight("E", 5f, true, 3, 1, RecapFight.Result.OTHER)));
        assertTrue(a.betterThan(null));
    }

    @Test
    void formats() {
        assertEquals("3", RecapFormat.kd(3.0, ','));
        assertEquals("2,33", RecapFormat.kd(7 / 3.0, ','));
        assertEquals("5:07", RecapFormat.playtime(307_000));
        assertEquals("1:05:07", RecapFormat.playtime(3_907_000));
        assertEquals("+12 345", RecapFormat.signed(12_345));
        assertEquals("−800", RecapFormat.signed(-800));
        assertEquals("0", RecapFormat.signed(0));
        assertEquals("1 000 000", RecapFormat.group(1_000_000));
        assertEquals("one", RecapFormat.plural(21));
        assertEquals("few", RecapFormat.plural(3));
        assertEquals("many", RecapFormat.plural(12));
    }

    @Test
    void balanceLines() {
        assertEquals(OptionalLong.of(1_234_567), BalanceLine.parse("§eБаланс: §f1 234 567"));
        assertEquals(OptionalLong.of(1_500_000), BalanceLine.parse("Монеты » 1.5kk"));
        assertEquals(OptionalLong.of(900), BalanceLine.parse("Balance 900$"));
        assertTrue(BalanceLine.parse("Сапфиры: 120").isEmpty());
        assertTrue(BalanceLine.parse("Коины: 50").isEmpty());
        assertTrue(BalanceLine.parse("Онлайн: 300").isEmpty());
        assertTrue(BalanceLine.parse("Баланс: —").isEmpty());
        assertEquals(OptionalLong.of(42), BalanceLine.find(List.of("#12 -◆-", "Монетки: 42", "Баланс: 7")));
    }

    @Test
    void linesAndCard() {
        Function<String, String> tr = key -> switch (key.substring(RecapLines.P.length())) {
            case "best.vs" -> "vs %s";
            case "best.damage" -> "%s dmg";
            case "best.hits.one", "best.hits.few", "best.hits.many" -> "%d hits";
            case "best.totems.one", "best.totems.few", "best.totems.many" -> "%d totems";
            default -> key.substring(key.lastIndexOf('.') + 1);
        };
        SessionRecap r = new SessionRecap("Me", "mc.holyworld.ru", 0, 3_600_000, true, 5, 2, 7, 5, 3, 1,
                fight("Enemy", 18.5f, true, 7, 2, RecapFight.Result.WIN), null, 3);
        List<RecapLines.Tile> tiles = RecapLines.tiles(r, tr, ',');
        assertEquals(8, tiles.size());
        assertEquals("2,5", tiles.get(2).value());
        assertEquals("1:00:00", tiles.get(3).value());
        assertEquals("—", tiles.get(6).value());
        assertEquals("+3", tiles.get(7).value());
        assertEquals("vs Enemy", RecapLines.bestTitle(r.best(), tr));
        assertEquals("18,5 dmg · 7 hits · 2 totems · win · 0:42", RecapLines.bestDetails(r.best(), tr, ','));
        assertEquals("none", RecapLines.bestTitle(null, tr));

        RecapCard.Data data = new RecapCard.Data("Me", "mc.holyworld.ru · 25.09.2026 · 1:00:00", "SESSION RECAP", tiles,
                "BEST FIGHT", "vs Enemy", RecapLines.bestDetails(r.best(), tr, ','), "Skirmish", 1.0);
        BufferedImage image = RecapCard.render(data);
        Theme theme = Theme.get();
        assertEquals(Math.round(theme.num("layout.recap_card.width")), image.getWidth());
        assertEquals(Math.round(theme.num("layout.recap_card.height")), image.getHeight());
        String preview = System.getenv("SKIRMISH_RECAP_PREVIEW");
        if (preview != null) {
            try {
                javax.imageio.ImageIO.write(RecapCard.render(new RecapCard.Data(data.title(), data.subtitle(), data.badge(),
                        data.tiles(), data.bestLabel(), data.bestTitle(), data.bestDetail(), data.footer(), 2.0)), "png", Path.of(preview).toFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ---- module metadata, lang and theme ----

    private static List<Module> modules() {
        return List.of(new TalismanBadgesModule(), new HwTooltipsModule(), new SessionRecapModule());
    }

    private static JsonObject lang(String ns, String code) throws IOException {
        try (InputStream in = SessionRecapTest.class.getResourceAsStream("/assets/" + ns + "/lang/" + code + ".json")) {
            assertNotNull(in, ns + "/" + code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void idsCategoriesAndEnglishNames() throws IOException {
        Map<String, Category> expected = Map.of("talisman_badges", Category.UTILITY, "hw_tooltips", Category.UTILITY,
                "session_recap", Category.INTERFACE);
        Map<String, String> names = Map.of("talisman_badges", "Item Badges", "hw_tooltips", "HolyWorld Tooltips",
                "session_recap", "Session Recap");
        for (Module m : modules()) {
            assertEquals(expected.get(m.id()), m.category(), m.id());
            assertEquals(m.id(), m.featureId());
            String ns = "skirmish_" + m.id();
            JsonObject ru = lang(ns, "ru_ru");
            JsonObject en = lang(ns, "en_us");
            assertEquals(en.keySet(), ru.keySet(), ns);
            assertEquals(names.get(m.id()), ru.get(m.nameKey()).getAsString());
            assertEquals(names.get(m.id()), en.get(m.nameKey()).getAsString());
            Set<String> keys = ru.keySet();
            assertTrue(keys.contains(m.descriptionKey()));
            for (Setting<?> s : m.settings()) {
                assertTrue(keys.contains(s.translationKey()), s.translationKey());
                assertTrue(keys.contains(s.translationKey() + ".tooltip"), s.translationKey());
                if (s instanceof EnumSetting<?> e) {
                    for (Object v : e.values()) {
                        String key = e.translationKey() + "." + ((Enum<?>) v).name().toLowerCase(java.util.Locale.ROOT);
                        assertTrue(keys.contains(key), key);
                    }
                }
            }
        }
    }

    /** Every literal "skirmish.session_recap.…" key in the sources and in the tests' lookups exists. */
    @Test
    void literalRecapKeysExist() throws IOException {
        Set<String> keys = lang("skirmish_session_recap", "ru_ru").keySet();
        Pattern literal = Pattern.compile("P \\+ \"([a-z_.]*[a-z_])\"");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/dev/skirmish/module/recap"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = literal.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    String key = RecapLines.P + m.group(1);
                    if (!key.endsWith(".hits") && !key.endsWith(".totems") && !key.endsWith(".result")) {
                        assertTrue(keys.contains(key), key + " in " + file.getFileName());
                    }
                }
            }
        }
        for (RecapFight.Result result : RecapFight.Result.values()) {
            assertTrue(keys.contains(RecapLines.P + "best.result." + result.name().toLowerCase(java.util.Locale.ROOT)));
        }
        for (String form : List.of("one", "few", "many")) {
            assertTrue(keys.contains(RecapLines.P + "best.hits." + form));
            assertTrue(keys.contains(RecapLines.P + "best.totems." + form));
        }
    }

    @Test
    void themeTokensExist() {
        Theme theme = Theme.get();
        for (String c : List.of("hwb_bg", "hwb_stats", "hwb_rune_immortality", "hwb_rune_restoration", "hwb_backpack", "hwb_tnt",
                "hwt_header", "hwt_line", "hwt_hint")) {
            theme.color(c);
        }
        for (String t : List.of("hwb_text", "recap_title", "recap_sub", "recap_section", "recap_best", "recap_tile_value",
                "recap_tile_label", "recap_status", "recap_card_sub", "recap_card_value", "recap_card_label", "recap_card_best")) {
            theme.text(t);
        }
        theme.radius("hw_badge");
        Pattern layout = Pattern.compile("L \\+ \"([a-z_]+)\"");
        Pattern prefix = Pattern.compile("String L = \"(layout\\.[a-z_.]+)\"");
        for (String dir : List.of("src/main/java/dev/skirmish/module/recap", "src/main/java/dev/skirmish/module/hwitems")) {
            try (Stream<Path> files = Files.walk(Path.of(dir))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String src = Files.readString(file, StandardCharsets.UTF_8);
                    Matcher p = prefix.matcher(src);
                    if (!p.find()) {
                        continue;
                    }
                    Matcher m = layout.matcher(src);
                    while (m.find()) {
                        theme.num(p.group(1) + m.group(1));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
