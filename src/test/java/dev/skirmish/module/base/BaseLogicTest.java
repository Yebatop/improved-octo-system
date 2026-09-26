package dev.skirmish.module.base;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseLogicTest {
    private static BaseData.Chest chest(int x, BaseData.Stack... items) {
        BaseData.Chest c = new BaseData.Chest();
        c.dim = "minecraft:overworld";
        c.x = x;
        c.items = List.of(items);
        return c;
    }

    private static BaseData.Stack st(String id, String name, int count) {
        return new BaseData.Stack(id, id, name, count);
    }

    @Test
    void totalsAndSearch() {
        List<StorageIndex.Total> totals = StorageIndex.totals(List.of(
                chest(1, st("minecraft:diamond", "Алмаз", 20), st("minecraft:golden_apple", "Золотое яблоко", 3)),
                chest(2, st("minecraft:diamond", "Алмаз", 44))));
        assertEquals(2, totals.size());
        StorageIndex.Total diamonds = totals.getFirst();
        assertEquals(64, diamonds.count());
        assertEquals(2, diamonds.where().size());
        assertEquals(44, diamonds.where().getFirst().count());
        assertEquals(1, StorageIndex.search(totals, "золот ябл").size());
        assertEquals(1, StorageIndex.search(totals, "golden").size());
        assertEquals(2, StorageIndex.search(totals, " ").size());
        assertEquals(0, StorageIndex.search(totals, "незерит").size());
    }

    @Test
    void diffAndText() {
        List<StorageIndex.Change> changes = StorageIndex.diff(
                List.of(st("a", "Алмаз", 10), st("g", "Яблоко", 5)),
                List.of(st("a", "Алмаз", 42), st("n", "Незерит", 1)));
        assertEquals(3, changes.size());
        assertEquals("+32 Алмаз, −5 Яблоко, +1 Незерит", StorageIndex.changeText(changes, 3));
        assertEquals("+32 Алмаз, …", StorageIndex.changeText(changes, 1));
        assertTrue(StorageIndex.diff(List.of(st("a", "A", 1)), List.of(st("a", "A", 1))).isEmpty());
    }

    @Test
    void lowStock() {
        List<StorageIndex.Total> totals = StorageIndex.totals(List.of(chest(1, st("t", "Тотем", 2))));
        List<StorageIndex.Low> low = StorageIndex.low(totals, Map.of("t", 5, "p", 16, "ok", 0), Map.of("p", "Жемчуг"));
        assertEquals(2, low.size());
        assertEquals("Жемчуг", low.getFirst().name());
        assertEquals(0, low.getFirst().have());
        assertEquals("Тотем", low.get(1).name());
        assertEquals("3×64 + 5", StorageIndex.stacks(197, 64));
        assertEquals("2×64", StorageIndex.stacks(128, 64));
        assertEquals("12", StorageIndex.stacks(12, 64));
    }

    @Test
    void durations() {
        DurationText.Found tax = DurationText.find("Налог через 2д 3ч");
        assertNotNull(tax);
        assertEquals("Налог", tax.label());
        assertEquals((2 * 24 + 3) * 3_600_000L, tax.millis());
        DurationText.Found clock = DurationText.find("Защита: 01:02:03");
        assertNotNull(clock);
        assertEquals("Защита", clock.label());
        assertEquals(3_723_000L, clock.millis());
        DurationText.Found min = DurationText.find("Эффект Сила — 5 мин");
        assertNotNull(min);
        assertEquals("Эффект Сила", min.label());
        assertEquals(300_000L, min.millis());
        assertEquals(4_800_000L, DurationText.find("Boost 1h 20m").millis());
        assertNull(DurationText.find("Регион игрока Player"));
        assertNull(DurationText.find("5 морковок"));
    }

    @Test
    void farmEta() {
        assertEquals(-1, FarmMath.etaMs(List.of(new FarmMath.Sample(0, 10, 100)), 600_000));
        assertEquals(0, FarmMath.etaMs(List.of(new FarmMath.Sample(0, 100, 100)), 600_000));
        List<FarmMath.Sample> growing = List.of(new FarmMath.Sample(0, 10, 100), new FarmMath.Sample(60_000, 20, 100));
        assertEquals(480_000, FarmMath.etaMs(growing, 600_000));
        // Harvested (fewer ripe than before): starts over.
        List<FarmMath.Sample> harvested = List.of(new FarmMath.Sample(0, 50, 100), new FarmMath.Sample(60_000, 0, 100));
        assertEquals(-1, FarmMath.etaMs(harvested, 600_000));
    }

    @Test
    void texts() {
        assertTrue(BaseText.mentions("Регион игрока Steve", "steve"));
        assertTrue(BaseText.mentions("Владелец: Steve | Налог", "Steve"));
        assertTrue(!BaseText.mentions("Регион игрока Steve_2", "Steve"));
        assertTrue(!BaseText.mentions("Регион игрока XSteve", "Steve"));
        assertTrue(BaseText.customGlyphs("\uE001\uE002 Меню"));
        assertTrue(!BaseText.customGlyphs("Большой сундук"));
        assertTrue(BaseText.container("minecraft:chest"));
        assertTrue(BaseText.container("minecraft:trapped_chest"));
        assertTrue(BaseText.container("minecraft:red_shulker_box"));
        assertTrue(BaseText.container("minecraft:barrel"));
        assertTrue(!BaseText.container("minecraft:furnace"));
        String[] u = {"д", "ч", "м"};
        assertEquals("2д 3ч", BaseText.span((51 * 60 + 5) * 60_000L, u));
        assertEquals("1ч 30м", BaseText.span(90 * 60_000L, u));
        assertEquals("3д", BaseText.span(3 * 86_400_000L, u));
        assertNull(BaseText.span(59 * 60_000L, u));
    }

    @Test
    void voxelTopFaceFacesUp() {
        int[] colors = new int[27];
        colors[(1 * 3 + 1) * 3 + 1] = 0xFFFF0000;
        VoxelRaster.Grid grid = new VoxelRaster.Grid(3, 3, 3, colors);
        int[] img = VoxelRaster.render(grid, 0.6f, 0.6f, 1f, 2, 64, 64);
        int lit = 0;
        int brightest = 0;
        for (int p : img) {
            if ((p >>> 24) != 0) {
                lit++;
                brightest = Math.max(brightest, (p >> 16) & 0xFF);
            }
        }
        assertTrue(lit > 20, "cube drawn");
        // The top face (full brightness) is visible from above.
        assertEquals(255, brightest);
        // Cut below the block: nothing left.
        int[] cut = VoxelRaster.render(grid, 0.6f, 0.6f, 1f, 0, 64, 64);
        for (int p : cut) {
            assertEquals(0, p >>> 24);
        }
    }
}
