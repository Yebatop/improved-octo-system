package dev.skirmish.module.fullbright;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullbrightMathTest {
    @Test
    void strengthMapsFromVanillaBrightToMax() {
        assertEquals(1f, FullbrightMath.targetGamma(0), 1e-6);
        assertEquals(FullbrightMath.MAX_GAMMA, FullbrightMath.targetGamma(100), 1e-6);
        assertEquals(FullbrightMath.MAX_GAMMA, FullbrightMath.targetGamma(250), 1e-6);
        assertTrue(FullbrightMath.targetGamma(10) > 1f);
    }

    @Test
    void darknessFadesTheBoostBackToVanilla() {
        float target = FullbrightMath.targetGamma(100);
        assertEquals(target, FullbrightMath.gamma(0.5f, target, 0f), 1e-6);
        // Full Darkness: the same gamma vanilla would use, so the effect is exactly as strong as without the mod.
        assertEquals(0.5f, FullbrightMath.gamma(0.5f, target, 1f), 1e-6);
        float half = FullbrightMath.gamma(0.5f, target, 0.5f);
        assertTrue(half > 0.5f && half < target);
        assertEquals(0.5f, FullbrightMath.gamma(0.5f, target, 7f), 1e-6);
    }

    @Test
    void neverDarkerThanTheUsersOwnGamma() {
        assertEquals(1f, FullbrightMath.gamma(1f, 0.2f, 0f), 1e-6);
    }
}
