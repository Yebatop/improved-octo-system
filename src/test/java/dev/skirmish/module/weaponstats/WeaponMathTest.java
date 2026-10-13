package dev.skirmish.module.weaponstats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaponMathTest {
    @Test
    void vanillaNumbers() {
        assertEquals(0, WeaponMath.sharpness(0), 1e-9);
        assertEquals(1.0, WeaponMath.sharpness(1), 1e-9);
        assertEquals(3.0, WeaponMath.sharpness(5), 1e-9);
        assertEquals(12.5, WeaponMath.smite(5), 1e-9);
        assertEquals(6.0, WeaponMath.effects(2, 0), 1e-9);
        assertEquals(-4.0, WeaponMath.effects(0, 1), 1e-9);
        assertEquals(15.0, WeaponMath.crit(10), 1e-9);
        assertEquals(0.625, WeaponMath.swingSeconds(1.6), 1e-9);
        assertEquals(16.0, WeaponMath.dps(10, 1.6), 1e-9);
        assertEquals(8.5, WeaponMath.sweep(10, 3), 1e-9);
    }
}
