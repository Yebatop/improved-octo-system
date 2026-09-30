package dev.skirmish.fx;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxFieldTest {
    @Test
    void particlesMoveFallAndDie() {
        FxField f = new FxField(10);
        Fx fx = f.spawn(FxShape.DOT).at(0, 10, 0).vel(2, 0, 0).gravity(10f).life(1f);
        f.update(0.1f);
        assertEquals(0.2, fx.x, 1e-6);
        assertEquals(-1.0, fx.vy, 1e-6);
        assertTrue(fx.y < 10);
        for (int i = 0; i < 12; i++) {
            f.update(0.1f);
        }
        assertEquals(0, f.size());
    }

    @Test
    void dragSlowsByFractionPerSecond() {
        FxField f = new FxField(10);
        Fx fx = f.spawn(FxShape.GLOW).vel(4, 0, 0).drag(0.25f).life(5f);
        for (int i = 0; i < 10; i++) {
            f.update(0.1f);
        }
        assertEquals(1.0, fx.vx, 1e-3);
    }

    @Test
    void fullFieldReusesTheOldest() {
        FxField f = new FxField(3);
        List<Fx> made = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            made.add(f.spawn(FxShape.GLOW).at(i, 0, 0).life(10f));
        }
        assertEquals(3, f.size());
        assertEquals(4.0, f.live().get(2).x, 1e-9);
    }

    @Test
    void laterRunsAfterItsDelay() {
        FxField f = new FxField(10);
        int[] runs = {0};
        f.later(0.25f, () -> runs[0]++);
        f.update(0.1f);
        f.update(0.1f);
        assertEquals(0, runs[0]);
        f.update(0.1f);
        assertEquals(1, runs[0]);
        f.update(0.1f);
        assertEquals(1, runs[0]);
    }

    @Test
    void sizeAndColourFollowAge() {
        FxField f = new FxField(10);
        Fx fx = f.spawn(FxShape.GLOW).life(1f).size(1f, 0f).color(0xFFFFFFFF, 0x00000000);
        f.update(0.05f);
        f.update(0.05f);
        f.update(0.1f);
        f.update(0.1f);
        f.update(0.1f);
        f.update(0.1f);
        assertEquals(0.5f, fx.size(), 1e-4);
        assertEquals(0x80, fx.color() >>> 24, 1);
    }
}
