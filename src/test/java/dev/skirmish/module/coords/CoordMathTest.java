package dev.skirmish.module.coords;

import dev.skirmish.waypoint.Waypoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordMathTest {
    @Test
    void overworldToNetherFloorsLikePortals() {
        assertEquals(0, CoordMath.toNether(0));
        assertEquals(1, CoordMath.toNether(15.9));
        assertEquals(125, CoordMath.toNether(1000));
        assertEquals(-1, CoordMath.toNether(-0.5));
        assertEquals(-1, CoordMath.toNether(-8));
        assertEquals(-2, CoordMath.toNether(-8.1));
        assertEquals(-125, CoordMath.toNether(-1000));
    }

    @Test
    void netherToOverworld() {
        assertEquals(0, CoordMath.toOverworld(0));
        assertEquals(800, CoordMath.toOverworld(100));
        assertEquals(804, CoordMath.toOverworld(100.5));
        assertEquals(-4, CoordMath.toOverworld(-0.5));
        assertEquals(-800, CoordMath.toOverworld(-100));
    }

    @Test
    void blockFloorsNegativeCoordinates() {
        assertEquals(-1, CoordMath.block(-0.01));
        assertEquals(0, CoordMath.block(0.99));
    }

    @Test
    void facingFollowsVanillaYaw() {
        assertEquals(CoordMath.Facing.SOUTH, CoordMath.facing(0));
        assertEquals(CoordMath.Facing.WEST, CoordMath.facing(90));
        assertEquals(CoordMath.Facing.NORTH, CoordMath.facing(180));
        assertEquals(CoordMath.Facing.NORTH, CoordMath.facing(-180));
        assertEquals(CoordMath.Facing.EAST, CoordMath.facing(-90));
        assertEquals(CoordMath.Facing.EAST, CoordMath.facing(270));
        assertEquals(CoordMath.Facing.SOUTH, CoordMath.facing(44));
        assertEquals(CoordMath.Facing.WEST, CoordMath.facing(46));
        assertEquals(CoordMath.Facing.SOUTH, CoordMath.facing(720 + 10));
        assertEquals(CoordMath.Facing.SOUTH, CoordMath.facing(-30));
        assertEquals("-Z", CoordMath.facing(180).axis());
    }

    @Test
    void onlyTheOldestDeathWaypointsOfThisServerAreDropped() {
        List<Waypoint> all = List.of(
                waypoint("a", "s1", "death", 1),
                waypoint("b", "s1", "death", 3),
                waypoint("c", "s1", "manual", 0),
                waypoint("d", "s1", "death", 2),
                waypoint("e", "s2", "death", 0),
                waypoint("f", "s1", "death", 4));
        assertEquals(List.of("d", "a"), DeathWaypoints.excess(all, "s1", 2));
        assertEquals(List.of(), DeathWaypoints.excess(all, "s1", 4));
        assertEquals(List.of(), DeathWaypoints.excess(all, "s2", 1));
        assertEquals(List.of("f", "b", "d", "a"), DeathWaypoints.excess(all, "s1", 0));
    }

    private static Waypoint waypoint(String id, String server, String source, long createdAt) {
        return new Waypoint(id, id, 0, 64, 0, "minecraft:overworld", server, createdAt, 0xFFFFFF, source);
    }
}
