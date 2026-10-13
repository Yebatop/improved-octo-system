package dev.skirmish.module.worldmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapMathTest {
    private static final float E = 1e-4f;

    @Test
    void northUpIsPlainOffset() {
        float[] at = MinimapMath.toMap(10, -20, 123f, false, 2);
        assertEquals(20f, at[0], E);
        assertEquals(-40f, at[1], E);
    }

    @Test
    void turnedMapPutsWhatYouFaceUp() {
        // Yaw 0 faces south (+Z): a block south of you is straight up, west (−X) is to the right.
        float[] ahead = MinimapMath.toMap(0, 5, 0f, true, 1);
        assertEquals(0f, ahead[0], E);
        assertEquals(-5f, ahead[1], E);
        float[] west = MinimapMath.toMap(-5, 0, 0f, true, 1);
        assertEquals(5f, west[0], E);
        assertEquals(0f, west[1], E);
        // Yaw 90 faces west: west is up, north (−Z) is to the right.
        float[] w = MinimapMath.toMap(-5, 0, 90f, true, 1);
        assertEquals(0f, w[0], E);
        assertEquals(-5f, w[1], E);
        float[] north = MinimapMath.toMap(0, -5, 90f, true, 1);
        assertEquals(5f, north[0], E);
        assertEquals(0f, north[1], E);
        // Facing north (yaw 180) is the plain north-up map.
        float[] plain = MinimapMath.toMap(3, 7, 180f, true, 1);
        assertEquals(3f, plain[0], E);
        assertEquals(7f, plain[1], E);
    }

    @Test
    void toWorldUndoesToMap() {
        for (float yaw : new float[]{0f, 37f, -120f, 270f}) {
            for (boolean rotate : new boolean[]{true, false}) {
                float[] at = MinimapMath.toMap(12.5, -3.25, yaw, rotate, 1.7);
                double[] back = MinimapMath.toWorld(at[0], at[1], yaw, rotate, 1.7);
                assertEquals(12.5, back[0], 1e-3);
                assertEquals(-3.25, back[1], 1e-3);
            }
        }
    }

    @Test
    void coverageIsSoftOnlyAtTheEdge() {
        assertEquals(1f, MinimapMath.coverage(0, 0, 100, true, 0), E);
        assertEquals(0f, MinimapMath.coverage(51, 0, 100, true, 0), E);
        assertEquals(0.5f, MinimapMath.coverage(50, 0, 100, true, 0), E);
        // The square's corner is cut by its radius, its edges are not.
        assertEquals(1f, MinimapMath.coverage(49, 0, 100, false, 10), E);
        assertEquals(0f, MinimapMath.coverage(49, 49, 100, false, 10), E);
        assertEquals(1f, MinimapMath.coverage(35, 35, 100, true, 0), E);
        assertEquals(0f, MinimapMath.coverage(40, 40, 100, true, 0), E);
    }

    @Test
    void pinMovesOutsideMarkersToTheRim() {
        assertTrue(MinimapMath.inside(10, 10, 100, true, 5));
        assertFalse(MinimapMath.inside(40, 40, 100, true, 5));
        float[] p = MinimapMath.pin(300, 400, 100, true, 5);
        assertEquals(27f, p[0], E);
        assertEquals(36f, p[1], E);
        float[] q = MinimapMath.pin(300, 150, 100, false, 5);
        assertEquals(45f, q[0], E);
        assertEquals(22.5f, q[1], E);
        float[] in = MinimapMath.pin(3, 4, 100, true, 5);
        assertEquals(3f, in[0], E);
        assertEquals(4f, in[1], E);
    }
}
