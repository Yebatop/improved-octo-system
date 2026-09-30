package dev.skirmish.module.food;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodMathTest {
    @Test
    void pointsPerIcon() {
        assertEquals(2, FoodMath.onIcon(7, 0));
        assertEquals(2, FoodMath.onIcon(7, 2));
        assertEquals(1, FoodMath.onIcon(7, 3));
        assertEquals(0, FoodMath.onIcon(7, 4));
        assertEquals(1, FoodMath.onIcon(0.4f, 0));
    }

    @Test
    void eatingIsCapped() {
        assertEquals(20, FoodMath.foodAfter(16, 8));
        assertEquals(14, FoodMath.foodAfter(6, 8));
        assertEquals(14f, FoodMath.saturationAfter(6, 3f, 8, 12.8f), 1e-6);
        assertEquals(7.8f, FoodMath.saturationAfter(10, 1f, 8, 6.8f), 1e-6);
    }

    @Test
    void gains() {
        assertTrue(FoodMath.gains(6, 14, 3));
        assertFalse(FoodMath.gains(6, 14, 2));
        assertFalse(FoodMath.gains(6, 14, 7));
    }
}
