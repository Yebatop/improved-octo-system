package dev.skirmish.module.killcard;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillCardRenderTest {
    private static final Path OUT = Path.of("build", "killcard-test");
    private static final ZonedDateTime TIME = ZonedDateTime.of(2026, 9, 23, 21, 4, 17, 0, ZoneId.of("Europe/Moscow"));

    @BeforeAll
    static void headless() throws Exception {
        // Same mode Minecraft forces in net.minecraft.client.main.Main.
        System.setProperty("java.awt.headless", "true");
        Files.createDirectories(OUT);
    }

    @Test
    void rendersEveryTheme() throws Exception {
        for (CardTheme theme : CardTheme.values()) {
            KillCardData data = new KillCardData(stats("Yebatop", "Убийца_2009", true), CardText.english(), theme, gear(), 1.0);
            CardRenderer.Result result = CardRenderer.render(data);
            BufferedImage image = result.image();
            assertEquals(960, image.getWidth());
            assertEquals(540, image.getHeight());
            assertTrue(result.warnings().isEmpty(), result.warnings().toString());
            assertNotBlank(image);
            Path file = OUT.resolve("killcard_" + theme.name().toLowerCase(Locale.ROOT) + ".png");
            ImageIO.write(image, "png", file.toFile());
            BufferedImage back = ImageIO.read(file.toFile());
            assertEquals(960, back.getWidth());
            assertEquals(540, back.getHeight());
        }
    }

    @Test
    void rendersRussianLabelsUnknownDamageAndScale() throws Exception {
        CardText russian = CardText.load((key, fallback) -> RUSSIAN.getOrDefault(key.substring(CardText.PREFIX.length()), fallback));
        KillCardData data = new KillCardData(stats("Победитель", "ОченьДлинныйНикСоперника_XYZ", false), russian, CardTheme.NEON, gear(), 1.5);
        CardRenderer.Result result = CardRenderer.render(data);
        assertEquals(1440, result.image().getWidth());
        assertEquals(810, result.image().getHeight());
        assertNotBlank(result.image());
        ImageIO.write(result.image(), "png", OUT.resolve("killcard_neon_ru_hp_hidden.png").toFile());
    }

    @Test
    void cyrillicIsCoveredByTheChosenFonts() {
        String nick = "Убийца_2009";
        CardFonts fonts = CardFonts.forSample(nick + " ЭКИПИРОВКА СОПЕРНИКА");
        assertEquals(-1, fonts.font(java.awt.Font.BOLD, 40, nick).canDisplayUpTo(nick), "font " + fonts.family());
    }

    @Test
    void exporterSavesUniqueFiles(@TempDir Path dir) throws Exception {
        KillCardData data = new KillCardData(stats("Me", "Target", true), CardText.english(), CardTheme.ARCTIC, gear(), 1.0);
        CardExporter.Outcome first = CardExporter.export(data, dir, false);
        CardExporter.Outcome second = CardExporter.export(data, dir, false);
        assertNull(first.error());
        assertNotNull(first.file());
        assertNotNull(second.file());
        assertNull(first.clipboard());
        assertFalse(first.file().equals(second.file()));
        assertEquals("killcard_2026-09-23_21.04.17_Target.png", first.file().getFileName().toString());
        assertEquals("killcard_2026-09-23_21.04.17_Target_2.png", second.file().getFileName().toString());
        BufferedImage read = ImageIO.read(first.file().toFile());
        assertEquals(960, read.getWidth());
        assertEquals(540, read.getHeight());
    }

    @Test
    void sanitizesFileNames() {
        assertEquals("Notch", CardFiles.sanitize("Notch"));
        assertEquals("Убийца_2009", CardFiles.sanitize("Убийца_2009"));
        assertEquals("a_b_c", CardFiles.sanitize("a/b\\c"));
        assertEquals("unknown", CardFiles.sanitize("../"));
        assertEquals("unknown", CardFiles.sanitize(""));
    }

    @Test
    void tintMultipliesColor() {
        IconSource.Layer white = new IconSource.Layer("test:white", 1, 1, new int[]{0xFFFFFFFF}, 0xFF3366CC);
        BufferedImage image = IconComposer.compose(new IconSource.Flat(List.of(white)), false, 64);
        assertEquals(0xFF3366CC, image.getRGB(0, 0));
        BufferedImage cube = IconComposer.compose(new IconSource.Cube(white, white, white), true, 64);
        assertEquals(64, cube.getWidth());
        assertTrue((cube.getRGB(32, 32) >>> 24) > 0, "cube center is drawn");
        assertEquals(0, cube.getRGB(0, 0) >>> 24, "cube corner stays transparent");
    }

    @Test
    void labelsFallBackToEnglish() {
        CardText text = CardText.load((key, fallback) -> key.endsWith(".title") ? "" : fallback);
        assertEquals("KILL CONFIRMED", text.title());
        assertEquals(6, text.slotNames().size());
        assertEquals("12.3 s", CardRenderer.formatDuration(12_340, "s"));
        assertEquals("1:05", CardRenderer.formatDuration(65_900, "s"));
    }

    // ---- fake data ----

    private static CardStats stats(String killer, String victim, boolean damageKnown) {
        return new CardStats(killer, victim, damageKnown, 37.5f, 14, 9, 2, 48_700, TIME, "lite.holyworld.ru", false);
    }

    private static List<CardItem> gear() {
        return List.of(
                new CardItem("minecraft:netherite_helmet", "Netherite Helmet", 1, 0.83f, true, flat(0xFF4A4042, 0xFF6D6466), "test"),
                new CardItem("minecraft:leather_chestplate", "Leather Tunic", 1, -1, false, tinted(0xFFB02E26), "test"),
                CardItem.EMPTY,
                new CardItem("minecraft:diamond_boots", "Diamond Boots", 1, 0.21f, true, flat(0xFF2BC7AC, 0xFF1D8C79), "test"),
                new CardItem("minecraft:obsidian", "Obsidian", 64, -1, false, cube(), "test"),
                new CardItem("minecraft:shield", "Shield", 1, 0.5f, false, null, "missing: special renderer"));
    }

    private static IconSource flat(int body, int edge) {
        int[] px = new int[16 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean shape = x >= 2 && x <= 13 && y >= 3 && y <= 12 && !(y > 8 && x > 5 && x < 10);
                boolean border = shape && (x == 2 || x == 13 || y == 3 || y == 12);
                px[y * 16 + x] = shape ? (border ? edge : body) : 0;
            }
        }
        return new IconSource.Flat(List.of(new IconSource.Layer("test:item", 16, 16, px, -1)));
    }

    private static IconSource tinted(int tint) {
        int[] base = new int[16 * 16];
        int[] overlay = new int[16 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean body = x >= 1 && x <= 14 && y >= 2 && y <= 14 && !(y < 6 && x > 4 && x < 11);
                base[y * 16 + x] = body ? 0xFFD8D8D8 - ((x + y) % 3) * 0x00101010 : 0;
                overlay[y * 16 + x] = body && y == 14 ? 0xFF6B4A2B : 0;
            }
        }
        return new IconSource.Flat(List.of(new IconSource.Layer("test:leather", 16, 16, base, tint),
                new IconSource.Layer("test:leather_overlay", 16, 16, overlay, -1)));
    }

    private static IconSource cube() {
        int[] px = new int[16 * 16];
        for (int i = 0; i < px.length; i++) {
            px[i] = ((i * 7) % 5 == 0) ? 0xFF3B2754 : 0xFF100C1C;
        }
        IconSource.Layer face = new IconSource.Layer("test:obsidian", 16, 16, px, -1);
        return new IconSource.Cube(face, face, face);
    }

    private static final Map<String, String> RUSSIAN = Map.ofEntries(
            Map.entry("title", "КИЛЛ ЗАСЧИТАН"), Map.entry("killer", "победитель"), Map.entry("victim", "повержен"),
            Map.entry("gear", "ЭКИПИРОВКА СОПЕРНИКА"), Map.entry("slot.head", "Шлем"), Map.entry("slot.chest", "Нагрудник"),
            Map.entry("slot.legs", "Поножи"), Map.entry("slot.feet", "Ботинки"), Map.entry("slot.mainhand", "Основная рука"),
            Map.entry("slot.offhand", "Вторая рука"), Map.entry("damage", "НАНЕСЕНО УРОНА"), Map.entry("damage_unknown", "н/д"),
            Map.entry("damage_unknown_note", "сервер скрывает здоровье"), Map.entry("totems", "СНЕСЕНО ТОТЕМОВ"),
            Map.entry("totems_note", "у соперника"), Map.entry("duration", "ДЛИТЕЛЬНОСТЬ БОЯ"), Map.entry("seconds", "с"),
            Map.entry("hits", "УДАРЫ"), Map.entry("hits_note", "нанесено / получено"), Map.entry("server", "Сервер"),
            Map.entry("date_pattern", "dd.MM.yyyy HH:mm:ss"));

    private static void assertNotBlank(BufferedImage image) {
        Set<Integer> colors = new HashSet<>();
        int background = image.getRGB(image.getWidth() / 2, 4);
        int different = 0;
        int total = 0;
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int rgb = image.getRGB(x, y);
                colors.add(rgb);
                if (Math.abs(((rgb >> 16) & 0xFF) - ((background >> 16) & 0xFF)) + Math.abs(((rgb >> 8) & 0xFF) - ((background >> 8) & 0xFF))
                        + Math.abs((rgb & 0xFF) - (background & 0xFF)) > 40) {
                    different++;
                }
                total++;
            }
        }
        assertTrue(colors.size() > 200, "only " + colors.size() + " colors");
        assertTrue(different > total / 20, "only " + different + " of " + total + " pixels differ from the background");
    }
}
