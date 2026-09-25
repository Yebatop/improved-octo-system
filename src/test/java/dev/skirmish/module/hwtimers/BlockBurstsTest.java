package dev.skirmish.module.hwtimers;

import dev.skirmish.module.hwtimers.TimerTable.TimerDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockBurstsTest {
    private static final TimerTable TABLE = TimerTable.bundled();

    private static List<BlockBursts.Burst> evaluate(BlockBursts bursts, long now, String... running) {
        List<String> on = List.of(running);
        return bursts.evaluate(TABLE.timers(), on::contains, now);
    }

    @Test
    void iceSphereIsAnIceWaveAndNotAlsoABox() {
        BlockBursts bursts = new BlockBursts();
        for (int i = 0; i < 20; i++) {
            bursts.appear(i, i % 2 == 0 ? "minecraft:ice" : "minecraft:packed_ice", 2.0, 1_000);
        }
        List<BlockBursts.Burst> found = evaluate(bursts, 1_050);
        assertEquals(1, found.size());
        assertEquals("ice_wave", found.getFirst().def().id());
        assertEquals(20, found.getFirst().positions().size());
        assertEquals(0, bursts.pending());
    }

    @Test
    void restOfARunningSphereJoinsIt() {
        BlockBursts bursts = new BlockBursts();
        for (int i = 0; i < 12; i++) {
            bursts.appear(100 + i, "minecraft:ice", 1.5, 2_000);
        }
        List<BlockBursts.Burst> found = evaluate(bursts, 2_050, "ice_wave");
        assertEquals(1, found.size());
        assertEquals("ice_wave", found.getFirst().def().id());
    }

    @Test
    void boxOfAnyBlockCloseToMeIsATrap() {
        BlockBursts bursts = new BlockBursts();
        for (int i = 0; i < 12; i++) {
            bursts.appear(i, "minecraft:obsidian", 1.8, 500);
        }
        List<BlockBursts.Burst> found = evaluate(bursts, 700);
        assertEquals(List.of("trap_box"), found.stream().map(b -> b.def().id()).toList());
    }

    @Test
    void fewOrFarOrOldBlocksAreNothing() {
        BlockBursts bursts = new BlockBursts();
        for (int i = 0; i < 5; i++) {
            bursts.appear(i, "minecraft:stone", 1.0, 0);
        }
        for (int i = 0; i < 20; i++) {
            bursts.appear(50 + i, "minecraft:stone", 5.5, 0);
        }
        assertTrue(evaluate(bursts, 100).isEmpty(), "5 close blocks and 20 blocks outside the trap radius");
        BlockBursts old = new BlockBursts();
        for (int i = 0; i < 20; i++) {
            old.appear(i, "minecraft:stone", 1.0, 0);
        }
        assertTrue(evaluate(old, 3_000).isEmpty(), "a box built over three seconds is not a trap");
    }

    @Test
    void obsidianBrokenRightAfterANearbyExplosionIsARaidBlock() {
        BlockBursts bursts = new BlockBursts();
        List<TimerDef> defs = TABLE.timers();
        assertNull(bursts.broken(1, 10, 64, 10, "minecraft:obsidian", 5, 0, defs), "mined by hand");
        assertTrue(bursts.explosion(12, 64, 12, 4f, 1_000).isEmpty(), "the hand-mined block is long gone");
        assertEquals("raid_block", bursts.broken(2, 10.5, 64.5, 10.5, "minecraft:obsidian", 5, 1_200, defs).id());
        assertEquals("raid_block", bursts.broken(3, 10.5, 64.5, 10.5, "minecraft:crying_obsidian", 5, 1_200, defs).id());
        assertNull(bursts.broken(4, 10.5, 64.5, 10.5, "minecraft:stone", 5, 1_200, defs));
        assertNull(bursts.broken(5, 10.5, 64.5, 10.5, "minecraft:obsidian", 60, 1_200, defs), "too far from me");
        assertNull(bursts.broken(6, 40, 64, 40, "minecraft:obsidian", 5, 1_200, defs), "too far from the blast");
        assertNull(bursts.broken(7, 10.5, 64.5, 10.5, "minecraft:obsidian", 5, 4_000, defs), "long after the blast");
    }

    @Test
    void blockUpdateJustBeforeTheExplosionPacketStillCounts() {
        BlockBursts bursts = new BlockBursts();
        List<TimerDef> defs = TABLE.timers();
        assertNull(bursts.broken(42, 10.5, 64.5, 10.5, "minecraft:obsidian", 5, 1_000, defs));
        assertNull(bursts.broken(43, 90.5, 64.5, 90.5, "minecraft:obsidian", 5, 1_000, defs));
        List<BlockBursts.Break> found = bursts.explosion(12, 64, 12, 4f, 1_100);
        assertEquals(1, found.size(), "only the block within the blast's reach");
        assertEquals(42L, found.getFirst().pos());
        assertEquals("raid_block", found.getFirst().def().id());
        assertNull(bursts.broken(44, 10.5, 64.5, 10.5, "minecraft:obsidian", 5, 5_000, defs));
        assertTrue(bursts.explosion(12, 64, 12, 4f, 5_400).isEmpty(), "400 ms is too early to be the same blast");
    }
}
