package dev.skirmish.module.killcam;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayBufferTest {
    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(2, 2);
    private static final UUID C = new UUID(3, 3);

    private static TrackSample at(double x, float yaw) {
        TrackSample s = new TrackSample();
        s.x = x;
        s.y = 64;
        s.z = -x;
        s.headYaw = yaw;
        s.bodyYaw = yaw;
        return s;
    }

    private static void record(ReplayBuffer buffer, UUID uuid, TrackSample sample) {
        buffer.write(buffer.trackFor(uuid, uuid.toString(), false), sample);
    }

    @Test
    void wrapAroundKeepsOnlyTheLastCapacityTicks() {
        ReplayBuffer buffer = new ReplayBuffer(10, 4, 16);
        for (int tick = 0; tick < 25; tick++) {
            assertEquals(tick, buffer.beginTick());
            record(buffer, A, at(tick, 0));
        }
        int track = buffer.findTrack(A);
        assertEquals(24, buffer.currentTick());
        assertEquals(15, buffer.oldestTick());
        assertFalse(buffer.has(track, 14), "overwritten by tick 24");
        assertFalse(buffer.has(track, 25), "future tick");
        TrackSample out = new TrackSample();
        assertTrue(buffer.read(track, 15, out));
        assertEquals(15, out.x);
        assertTrue(buffer.read(track, 24, out));
        assertEquals(24, out.x);
        assertEquals(-24, out.z);
    }

    @Test
    void gapsAreAbsentAndNotInterpolatedAcross() {
        ReplayBuffer buffer = new ReplayBuffer(20, 4, 16);
        for (int tick = 0; tick < 10; tick++) {
            buffer.beginTick();
            if (tick < 3 || tick > 5) {
                record(buffer, A, at(tick, 0));
            }
        }
        int track = buffer.findTrack(A);
        TrackSample out = new TrackSample();
        assertFalse(buffer.has(track, 4));
        assertFalse(buffer.sample(track, 4.5, out));
        assertTrue(buffer.sample(track, 2.5, out), "tick 2 exists, tick 3 does not");
        assertEquals(2, out.x, 1e-9);
        assertEquals(6, buffer.firstPresent(track, 3, 9));
        assertEquals(-1, buffer.firstPresent(track, 3, 5));
    }

    @Test
    void sampleInterpolatesPositionAnglesAndSwing() {
        ReplayBuffer buffer = new ReplayBuffer(20, 4, 16);
        buffer.beginTick();
        TrackSample first = at(0, 170);
        first.attackAnim = 0.8F;
        first.pitch = 10;
        first.hurtTime = 7;
        first.walkPos = 1;
        record(buffer, A, first);
        buffer.beginTick();
        TrackSample second = at(2, -170);
        second.attackAnim = 0.0F;
        second.pitch = 20;
        second.hurtTime = 6;
        second.walkPos = 3;
        record(buffer, A, second);

        TrackSample out = new TrackSample();
        assertTrue(buffer.sample(buffer.findTrack(A), 0.5, out));
        assertEquals(1.0, out.x, 1e-9);
        assertEquals(-1.0, out.z, 1e-9);
        assertEquals(180.0F, Math.abs(ReplayBuffer.wrapDegrees(out.headYaw)), 1e-4, "short way across ±180");
        assertEquals(15.0F, out.pitch, 1e-4);
        assertEquals(0.9F, out.attackAnim, 1e-4, "swing end wraps like getAttackAnim");
        assertEquals(2.0F, out.walkPos, 1e-4);
        assertEquals(7, out.hurtTime, "discrete values come from the earlier tick");
    }

    @Test
    void lastRecordedTickIsSampledAfterRecordingStops() {
        ReplayBuffer buffer = new ReplayBuffer(240, 4, 16);
        for (int tick = 0; tick < 300; tick++) {
            buffer.beginTick();
            record(buffer, A, at(tick, 0));
            record(buffer, B, at(-tick, 0));
        }
        long end = buffer.currentTick();
        TrackSample out = new TrackSample();
        assertTrue(buffer.sample(buffer.findTrack(B), end, out));
        assertEquals(-end, out.x, 1e-9);
        assertTrue(buffer.sample(buffer.findTrack(B), end - 1e-9, out));
        assertEquals(end, buffer.lastTick(buffer.findTrack(B)));
        assertEquals(end, buffer.lastPresent(buffer.findTrack(B), end + 5, end - 20));
    }

    @Test
    void teleportsAreNotInterpolated() {
        ReplayBuffer buffer = new ReplayBuffer(20, 4, 16);
        buffer.beginTick();
        record(buffer, A, at(0, 0));
        buffer.beginTick();
        record(buffer, A, at(100, 0));
        TrackSample out = new TrackSample();
        assertTrue(buffer.sample(buffer.findTrack(A), 0.75, out));
        assertEquals(0, out.x, 1e-9);
    }

    @Test
    void equipmentIsLookedUpByChangeEvents() {
        ReplayBuffer buffer = new ReplayBuffer(50, 4, 16);
        int track = -1;
        for (int tick = 0; tick < 10; tick++) {
            buffer.beginTick();
            track = buffer.trackFor(A, "a", false);
            buffer.write(track, at(tick, 0));
            if (tick == 2) {
                buffer.setEquipment(track, 4, "sword");
            } else if (tick == 5) {
                buffer.setEquipment(track, 4, "axe");
                buffer.setEquipment(track, 0, "helmet");
            }
        }
        assertNull(buffer.equipmentAt(track, 4, 1));
        assertEquals("sword", buffer.equipmentAt(track, 4, 2));
        assertEquals("sword", buffer.equipmentAt(track, 4, 4));
        assertEquals("axe", buffer.equipmentAt(track, 4, 5));
        assertEquals("axe", buffer.equipmentAt(track, 4, 9));
        assertEquals("helmet", buffer.equipmentAt(track, 0, 9));
        assertNull(buffer.equipmentAt(track, 1, 9));
        assertEquals("axe", buffer.latestEquipment(track, 4));
        assertEquals(3, buffer.equipmentChanges(track));
    }

    @Test
    void equipmentRingEvictsIntoTheBaseState() {
        ReplayBuffer buffer = new ReplayBuffer(500, 4, 16);
        int track = -1;
        int changes = ReplayBuffer.EQUIPMENT_EVENTS + 10;
        for (int tick = 0; tick < changes; tick++) {
            buffer.beginTick();
            track = buffer.trackFor(A, "a", false);
            buffer.write(track, at(tick, 0));
            buffer.setEquipment(track, tick % 2 == 0 ? 4 : 5, "item" + tick);
        }
        assertEquals(ReplayBuffer.EQUIPMENT_EVENTS, buffer.equipmentChanges(track));
        assertEquals("item" + (changes - 2), buffer.equipmentAt(track, 4, changes));
        assertEquals("item" + (changes - 1), buffer.equipmentAt(track, 5, changes));
        long firstKept = changes - ReplayBuffer.EQUIPMENT_EVENTS;
        assertEquals("item" + firstKept, buffer.equipmentAt(track, 4, firstKept));
        assertEquals("item" + (firstKept - 1), buffer.equipmentAt(track, 5, firstKept), "base state of the evicted change");
        assertEquals(10, buffer.equipmentOverflows(), "evicted inside the window");
    }

    @Test
    void tracksAreReusedOnlyAfterTheirDataExpired() {
        ReplayBuffer buffer = new ReplayBuffer(10, 2, 16);
        buffer.beginTick();
        record(buffer, A, at(0, 0));
        record(buffer, B, at(0, 0));
        int trackA = buffer.findTrack(A);
        int generation = buffer.generation(trackA);
        assertEquals(ReplayBuffer.NO_TRACK, buffer.trackFor(C, "c", false), "both tracks hold data in the window");
        for (int tick = 1; tick < 12; tick++) {
            buffer.beginTick();
            record(buffer, B, at(tick, 0));
        }
        int trackC = buffer.trackFor(C, "c", false);
        assertEquals(trackA, trackC, "A's data expired, its arrays are reused");
        assertNotEquals(generation, buffer.generation(trackC));
        assertEquals(ReplayBuffer.NO_TRACK, buffer.findTrack(A));
        assertFalse(buffer.has(trackC, 0));
        assertEquals(2, buffer.allocatedTracks());
    }

    @Test
    void eventsAreStampedWithTheNextTickAndRingOverwritesOldest() {
        ReplayBuffer buffer = new ReplayBuffer(10, 2, 3);
        buffer.beginTick();
        buffer.addEvent(ReplayBuffer.EVENT_HIT, 0, 1, "minecraft:player_attack");
        assertEquals(1, buffer.eventTick(0));
        for (int i = 0; i < 4; i++) {
            buffer.addEvent(ReplayBuffer.EVENT_CRIT, i, -1, null);
        }
        assertEquals(3, buffer.eventCount());
        assertEquals(ReplayBuffer.EVENT_CRIT, buffer.eventType(0));
        assertEquals(1, buffer.eventA(0));
        assertEquals(3, buffer.eventA(2));
    }

    @Test
    void clearForgetsEverything() {
        ReplayBuffer buffer = new ReplayBuffer(10, 2, 8);
        buffer.beginTick();
        int track = buffer.trackFor(A, "a", true);
        buffer.write(track, at(1, 0));
        buffer.setEquipment(track, 0, "helmet");
        buffer.addEvent(ReplayBuffer.EVENT_TOTEM, track, -1, null);
        buffer.clear();
        assertTrue(buffer.isEmpty());
        assertEquals(ReplayBuffer.NO_TRACK, buffer.findTrack(A));
        assertEquals(0, buffer.eventCount());
        buffer.beginTick();
        int again = buffer.trackFor(A, "a", true);
        assertEquals(track, again);
        assertFalse(buffer.has(again, 0));
        assertNull(buffer.equipmentAt(again, 0, 1));
    }
}
