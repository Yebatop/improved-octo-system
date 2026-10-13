package dev.skirmish.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionLedgerTest {
    @Test
    void remembersTheFirstUserValueAndRestoresIt() {
        OptionLedger.Store store = new OptionLedger.Store("saved");
        OptionLedger ledger = new OptionLedger(store);
        ledger.remember("bobView", "true");
        ledger.written("bobView", "false");
        // A second apply (e.g. after a restart with the module on) must not overwrite the user's value.
        ledger.remember("bobView", "false");
        ledger.written("bobView", "false");
        assertEquals("true", ledger.restoreValue("bobView", "false"));
        assertEquals(Set.of("bobView"), ledger.keys());
    }

    @Test
    void keepsAValueTheUserChangedByHand() {
        OptionLedger ledger = new OptionLedger(new OptionLedger.Store("saved"));
        ledger.remember("fovEffectScale", "1.0");
        ledger.written("fovEffectScale", "0.0");
        assertNull(ledger.restoreValue("fovEffectScale", "0.5"));
        assertNull(ledger.restoreValue("unknown", "0.5"));
    }

    @Test
    void survivesAReloadThroughTheStoredString() {
        OptionLedger.Store store = new OptionLedger.Store("saved");
        new OptionLedger(store).remember("toggleSprint", "false");
        new OptionLedger(store).written("toggleSprint", "true");
        OptionLedger.Store reloaded = new OptionLedger.Store("saved");
        reloaded.set(store.get());
        assertEquals("false", new OptionLedger(reloaded).restoreValue("toggleSprint", "true"));
    }

    @Test
    void clearAndBrokenJson() {
        OptionLedger.Store store = new OptionLedger.Store("saved");
        OptionLedger ledger = new OptionLedger(store);
        ledger.remember("a", "1");
        ledger.clear();
        assertFalse(ledger.has("a"));
        assertEquals("", store.get());
        store.set("{not json");
        assertTrue(ledger.keys().isEmpty());
        ledger.remember("a", "1");
        assertTrue(ledger.has("a"));
    }

    @Test
    void storeIsHiddenAndIgnoresMenuReset() {
        OptionLedger.Store store = new OptionLedger.Store("saved");
        store.set("{\"a\":{\"user\":\"1\",\"ours\":\"2\"}}");
        store.reset();
        assertTrue(store.get().contains("\"a\""));
        assertFalse(store.isVisible());
        assertTrue(store.isPersisted());
    }
}
