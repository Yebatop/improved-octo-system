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
        Fx fx = f.spawn(FxSprite.DUST).at(0, 10, 0).vel(2, 0, 0).gravity(10f).life(1f);
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
        Fx fx = f.spawn(FxSprite.GLOW).vel(4, 0, 0).drag(0.25f).life(5f);
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
            made.add(f.spawn(FxSprite.GLOW).at(i, 0, 0).life(10f));
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
        Fx fx = f.spawn(FxSprite.GLOW).life(1f).size(1f, 0f).color(0xFFFFFFFF, 0x00000000);
        f.update(0.05f);
        f.update(0.05f);
        f.update(0.1f);
        f.update(0.1f);
        f.update(0.1f);
        f.update(0.1f);
        assertEquals(0.5f, fx.size(), 1e-4);
        assertEquals(0x80, fx.color() >>> 24, 1);
    }

    @Test
    void ribbonPointsFadeAfterStop() {
        FxField f = new FxField(10);
        FxRibbon r = f.ribbon();
        r.pointLife = 0.3f;
        r.push(0, 0, 0);
        f.update(0.1f);
        r.push(1, 0, 0);
        f.update(0.1f);
        r.push(1.0001, 0, 0);
        assertEquals(2, r.count, "a point too close to the last one moves it instead of adding");
        r.stop();
        r.push(5, 0, 0);
        assertEquals(2, r.count);
        for (int i = 0; i < 5; i++) {
            f.update(0.1f);
        }
        assertTrue(f.ribbons().isEmpty());
    }

    @Test
    void shiftMovesEverythingIncludingOrbitCentres() {
        FxField f = new FxField(10);
        Fx a = f.spawn(FxSprite.GLOW).at(1, 2, 3).life(5f);
        Fx b = f.spawn(FxSprite.STAR4).orbit(0, 0, 1f, 0f, 0f).life(5f);
        FxRibbon r = f.ribbon();
        r.push(0, 0, 0);
        f.shift(2, -1);
        assertEquals(3.0, a.x, 1e-9);
        assertEquals(2.0, a.z, 1e-9);
        f.update(0.01f);
        assertEquals(3.0, b.x, 1e-6);
        assertEquals(-1.0, b.z, 1e-6);
        assertEquals(2.0, r.px[r.start], 1e-9);
    }

    @Test
    void animatedSpritesLoopTheirFrames() {
        FxField f = new FxField(10);
        Fx fx = f.spawn(FxSprite.FLAME).fps(10).life(5f);
        fx.frame0 = 0;
        f.update(0.05f);
        assertEquals(0, fx.frame());
        f.update(0.1f);
        assertEquals(1, fx.frame());
        for (int i = 0; i < 8; i++) {
            f.update(0.1f);
        }
        assertEquals(FxSprite.FLAME.frames > 0 ? (int) (fx.age * 10) % FxSprite.FLAME.frames : 0, fx.frame());
    }

    @Test
    void particlesBounceOffTheFloorThenRestOnIt() {
        FxField f = new FxField(10);
        Fx fx = f.spawn(FxSprite.SPARK).at(0, 1, 0).vel(1, -6, 0).gravity(10f).floor(0, 0.5f).life(10f);
        boolean bounced = false;
        for (int i = 0; i < 200; i++) {
            f.update(0.02f);
            assertTrue(fx.y >= 0, "never sinks under its floor");
            bounced |= fx.vy > 0;
        }
        assertTrue(bounced);
        assertTrue(fx.grounded);
        assertEquals(0.0, fx.y, 1e-9);
        assertEquals(0.0, fx.vx, 1e-3);
    }

    @Test
    void dropsLeaveASplatWhereTheyLand() {
        FxField f = new FxField(10);
        f.spawn(FxSprite.DROP).at(2, 1, 3).vel(0, -5, 0).floor(0, 0f).splat(0xFF800000).life(5f);
        for (int i = 0; i < 20; i++) {
            f.update(0.02f);
        }
        assertEquals(1, f.size());
        Fx splat = f.live().getFirst();
        assertEquals(FxSprite.SPLAT, splat.sprite);
        assertEquals(Fx.Orient.FLAT, splat.orient);
        assertEquals(2.0, splat.x, 0.2);
        assertTrue(splat.y > 0 && splat.y < 0.05);
    }

    @Test
    void noFloorMeansNoLanding() {
        FxField f = new FxField(10);
        Fx fx = f.spawn(FxSprite.SPARK).at(0, 1, 0).vel(0, -5, 0).life(10f);
        for (int i = 0; i < 5; i++) {
            f.update(0.1f);
        }
        assertTrue(fx.y < 0);
        assertTrue(!fx.grounded);
    }
}
