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

    /** The mockup's own sample data at 1x and 2x (compared side by side in docs/ui-compare/killcard.png). */
    @Test
    void rendersMockupSampleAt1xAnd2x() throws Exception {
        CardText russian = CardText.load((key, fallback) -> RUSSIAN.getOrDefault(key.substring(CardText.PREFIX.length()), fallback), 2);
        CardStats stats = new CardStats("[ВАШ НИК]", "GFk31AK", 6.5f, true, 34.5f, 18, 23, 2, 47_000, TIME, "holyworld", false,
                solidFace(0xFFB98B62), solidFace(0xFFC9A27A));
        for (double scale : new double[]{1.0, 2.0}) {
            CardRenderer.Result result = CardRenderer.render(new KillCardData(stats, russian, mockupGear(), scale));
            BufferedImage image = result.image();
            assertEquals((int) (800 * scale), image.getWidth());
            assertEquals((int) (420 * scale), image.getHeight());
            assertTrue(result.warnings().isEmpty(), result.warnings().toString());
            assertNotBlank(image);
            ImageIO.write(image, "png", OUT.resolve(scale == 1.0 ? "killcard_mockup.png" : "killcard_mockup@2x.png").toFile());
        }
    }

    @Test
    void rendersEnglishUnknownDamageAndMissingFaces() throws Exception {
        CardStats stats = new CardStats("Yebatop", "ОченьДлинныйНикСоперника_XYZ_2009", -1f, false, 0f, 0, 0, 1, 125_400, TIME, null, true,
                null, null);
        CardRenderer.Result result = CardRenderer.render(new KillCardData(stats, CardText.english(1), gear(), 1.0));
        assertNotBlank(result.image());
        ImageIO.write(result.image(), "png", OUT.resolve("killcard_en_unknown.png").toFile());
    }

    @Test
    void cyrillicIsCoveredByTheBundledFont() {
        assertEquals("Manrope", CardFonts.describe("Убийца_2009"));
    }

    @Test
    void exporterSavesUniqueFiles(@TempDir Path dir) throws Exception {
        CardStats stats = new CardStats("Me", "Target", 20f, true, 12f, 3, 4, 0, 5_000, TIME, "server", false, null, null);
        KillCardData data = new KillCardData(stats, CardText.english(0), gear(), 1.0);
        CardExporter.Outcome first = CardExporter.export(data, dir);
        CardExporter.Outcome second = CardExporter.export(data, dir);
        assertNull(first.error());
        assertNotNull(first.file());
        assertNotNull(second.file());
        assertNotNull(first.image());
        assertFalse(first.file().equals(second.file()));
        assertEquals("killcard_2026-09-23_21.04.17_Target.png", first.file().getFileName().toString());
        assertEquals("killcard_2026-09-23_21.04.17_Target_2.png", second.file().getFileName().toString());
        BufferedImage read = ImageIO.read(first.file().toFile());
        assertEquals(800, read.getWidth());
        assertEquals(420, read.getHeight());
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
    void labelsFallBackToEnglishAndPickPluralForms() {
        CardText text = CardText.load((key, fallback) -> key.endsWith(".badge") ? "" : fallback, 1);
        assertEquals("VICTORY", text.badge());
        assertEquals("totem popped", text.totemsLabel());
        assertEquals("totems popped", CardText.english(2).totemsLabel());
        CardText.load((key, fallback) -> RUSSIAN.getOrDefault(key.substring(CardText.PREFIX.length()), fallback), 5);
        assertEquals("тотема снесено", CardText.load((key, fallback) -> RUSSIAN.getOrDefault(key.substring(CardText.PREFIX.length()), fallback), 2).totemsLabel());
        assertEquals("тотемов снесено", CardText.load((key, fallback) -> RUSSIAN.getOrDefault(key.substring(CardText.PREFIX.length()), fallback), 11).totemsLabel());
        assertEquals("тотем снесён", CardText.load((key, fallback) -> RUSSIAN.getOrDefault(key.substring(CardText.PREFIX.length()), fallback), 21).totemsLabel());
        assertEquals("0:47", CardRenderer.formatDuration(47_900));
        assertEquals("2:05", CardRenderer.formatDuration(125_400));
    }

    // ---- fake data ----

    private static int[] solidFace(int argb) {
        int[] face = new int[64];
        java.util.Arrays.fill(face, argb);
        return face;
    }

    private static List<CardItem> mockupGear() {
        return List.of(
                new CardItem("minecraft:diamond_helmet", "Helmet", 1, 0.9f, false, flat(0xFF5CE1E6, 0xFF3AB8BD), "test"),
                new CardItem("minecraft:netherite_chestplate", "Chest", 1, 0.8f, false, flat(0xFF4B3F6B, 0xFF3A3054), "test"),
                new CardItem("minecraft:netherite_leggings", "Legs", 1, 0.4f, false, flat(0xFF4B3F6B, 0xFF3A3054), "test"),
                new CardItem("minecraft:netherite_boots", "Boots", 1, 0.9f, false, flat(0xFF4B3F6B, 0xFF3A3054), "test"),
                new CardItem("minecraft:netherite_sword", "Sword", 1, 0.1f, false, flat(0xFF4B3F6B, 0xFF3A3054), "test"),
                CardItem.EMPTY);
    }

    private static List<CardItem> gear() {
        return List.of(
                new CardItem("minecraft:netherite_helmet", "Netherite Helmet", 1, 0.83f, true, flat(0xFF4A4042, 0xFF6D6466), "test"),
                new CardItem("minecraft:leather_chestplate", "Leather Tunic", 1, -1, false, tinted(0xFFB02E26), "test"),
                CardItem.EMPTY,
                new CardItem("minecraft:diamond_boots", "Diamond Boots", 1, 0.21f, true, flat(0xFF2BC7AC, 0xFF1D8C79), "test"),
                new CardItem("minecraft:obsidian", "Obsidian", 64, -1, false, cube(), "test"),
                CardItem.EMPTY);
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
        assertTrue(different > total / 50, "only " + different + " of " + total + " pixels differ from the background");
    }

    private static final Map<String, String> RUSSIAN = Map.ofEntries(
            Map.entry("badge", "ПОБЕДА"), Map.entry("vs", "против"), Map.entry("remaining", "осталось %s HP"),
            Map.entry("killed", "убит"), Map.entry("damage", "урона нанесено"), Map.entry("totems.one", "тотем снесён"),
            Map.entry("totems.few", "тотема снесено"), Map.entry("totems.many", "тотемов снесено"),
            Map.entry("accuracy", "попаданий"), Map.entry("duration", "длительность"),
            Map.entry("gear", "Снаряжение соперника"), Map.entry("date_pattern", "dd.MM.yyyy"), Map.entry("decimal", ","));
}
