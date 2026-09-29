package dev.skirmish.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MaskedCoordsTest {
    /** The real line (screenshot, 2026-09-29). */
    @Test
    void goldenSpawnerLine() {
        MaskedCoords.Masked m = MaskedCoords.find("▶ Игрок Player_1 установил Золотой Спавнер на координатах 1*4*, 5, 1*9*");
        assertEquals("Игрок Player_1 установил Золотой Спавнер", m.what());
        assertEquals(5, m.y());
        assertEquals(10, m.xs().size());
        assertArrayEquals(new int[]{1040, 1049}, m.xs().getFirst());
        assertArrayEquals(new int[]{1940, 1949}, m.xs().getLast());
        assertArrayEquals(new int[]{1090, 1099}, m.zs().getFirst());
        assertEquals(100, m.cells());
    }

    @Test
    void rangesOfMasks() {
        List<int[]> neg = MaskedCoords.ranges("-2*");
        assertEquals(1, neg.size());
        assertArrayEquals(new int[]{-29, -20}, neg.getFirst());
        assertArrayEquals(new int[]{100, 199}, MaskedCoords.ranges("1**").getFirst());
        assertEquals(100, MaskedCoords.ranges("*5*5").size());
        // Too many combinations: one range over everything.
        List<int[]> wide = MaskedCoords.ranges("*1*1*1");
        assertEquals(1, wide.size());
        assertArrayEquals(new int[]{10101, 919191}, wide.getFirst());
        assertEquals(List.of(), MaskedCoords.ranges("abc"));
    }

    @Test
    void plainCoordinatesAreNotMasked() {
        assertNull(MaskedCoords.find("▶ Кубик находится на координатах 120 70 -45 [+метка]"));
        assertNull(MaskedCoords.find("продам 64 шт за 1 000"));
    }
}
