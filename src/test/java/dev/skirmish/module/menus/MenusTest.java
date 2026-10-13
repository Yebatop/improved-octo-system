package dev.skirmish.module.menus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenusTest {
    @Test
    void fadeGoesFromOneToZero() {
        assertEquals(1f, TransitionsModule.fadeAlpha(0, 700), 1e-6);
        assertEquals(0.25f, TransitionsModule.fadeAlpha(350, 700), 1e-6);
        assertEquals(0f, TransitionsModule.fadeAlpha(700, 700), 1e-6);
        assertEquals(0f, TransitionsModule.fadeAlpha(-5, 700), 1e-6);
    }

    @Test
    void tipsCycleThroughAll() {
        for (long t = 0; t < 100_000; t += 9000) {
            int i = MenuTips.index(t, 9000, 3);
            assertTrue(i >= 1 && i <= MenuTips.COUNT);
        }
        assertEquals(MenuTips.index(0, 9000, 0) % MenuTips.COUNT + 1, MenuTips.index(9000, 9000, 0));
    }
}
