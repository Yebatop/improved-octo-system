package dev.skirmish.module.hunt;

import dev.skirmish.util.MaskedCoords;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HuntMathTest {
    private static final MaskedCoords.Masked SPAWNER =
            MaskedCoords.find("▶ Игрок Player_1 установил Золотой Спавнер на координатах 1*4*, 5, 1*9*");

    @Test
    void cellsAndNearest() {
        assertEquals("1*4*", SPAWNER.xMask());
        assertEquals(-1, HuntMath.cellAt(SPAWNER.xs(), SPAWNER.zs(), 0, 0));
        // 1445, 1195: X range «1440–1449» is the 5th, Z range «1190–1199» the 2nd.
        assertEquals(4 * 10 + 1, HuntMath.cellAt(SPAWNER.xs(), SPAWNER.zs(), 1445.5, 1195.2));

        BitSet visited = new BitSet();
        HuntMath.Target t = HuntMath.nearest(SPAWNER.xs(), SPAWNER.zs(), visited, 0, 0);
        assertEquals(0, t.index());
        assertEquals(1045.0, t.x());
        assertEquals(1095.0, t.z());
        visited.set(0);
        // Next: X 1140…1149 × Z 1090…1099 (index 10) is nearer to 0,0 than X 1040…1049 × Z 1190…1199 (index 1).
        assertEquals(10, HuntMath.nearest(SPAWNER.xs(), SPAWNER.zs(), visited, 0, 0).index());
        visited.set(0, 100);
        assertNull(HuntMath.nearest(SPAWNER.xs(), SPAWNER.zs(), visited, 0, 0));
    }

    @Test
    void subjectAndMask() {
        assertArrayEquals(new String[]{"Золотой Спавнер", "Player_1"}, HuntMath.subject(SPAWNER.what()));
        assertArrayEquals(new String[]{"Что-то", ""}, HuntMath.subject("Что-то"));
        assertArrayEquals(new Character[]{'1', null, '4', null}, HuntMath.maskChars("1*4*"));
    }
}
