package dev.skirmish.module.toolsaver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRulesTest {
    @Test
    void thresholds() {
        assertEquals(6, ToolRules.usesLeft(1561, 1555));
        assertTrue(ToolRules.protect(6, 10));
        assertFalse(ToolRules.protect(11, 10));
        assertTrue(ToolRules.warn(150, 1561, 10, 10));
        assertFalse(ToolRules.warn(200, 1561, 10, 10));
        assertTrue(ToolRules.warn(18, 59, 10, 10));
    }

    @Test
    void bypassWindow() {
        assertTrue(ToolRules.bypass(1500, 1000, 700));
        assertFalse(ToolRules.bypass(1800, 1000, 700));
        assertFalse(ToolRules.bypass(1500, -1, 700));
    }
}
