package dev.skirmish.module.killcam.library;

import dev.skirmish.module.killcam.ReplayBuffer;
import dev.skirmish.module.killcam.TrackSample;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Live buffer → recording → file → recording → rebuilt buffer must play back the same states. */
class ReplayCaptureTest {
    private static final UUID ME = new UUID(1, 1);
    private static final UUID FOE = new UUID(2, 2);
    private static final UUID LATE = new UUID(3, 3);
    private static final UUID GONE = new UUID(4, 4);
    private static final String EMPTY = "empty";

    private static TrackSample at(double x, float yaw, int tick) {
        TrackSample s = new TrackSample();
        s.x = 100.3 + x;
        s.y = 70 + tick * 0.01;
        s.z = -x * 0.5;
        s.headYaw = yaw;
        s.bodyYaw = yaw;
        s.pitch = tick % 30;
        s.walkPos = tick * 0.3F;
        s.walkSpeed = 0.6F;
        s.attackAnim = (tick % 6) / 6F;
        s.health = 20 - tick * 0.1F;
        s.pose = (byte) (tick % 3);
        s.hurtTime = (byte) (tick % 10);
        s.anim = (byte) (tick % 8);
        s.useTicks = (short) tick;
        return s;
    }

    /** 60 ticks into a 40-tick buffer (so it wrapped); GONE leaves early, LATE appears late, FOE is away for a while. */
    private static ReplayBuffer live() {
        ReplayBuffer buffer = new ReplayBuffer(40, 8, 64);
        for (int tick = 0; tick < 60; tick++) {
            if (tick == 44) {
                buffer.addEvent(ReplayBuffer.EVENT_HIT, buffer.findTrack(FOE), buffer.findTrack(ME), "minecraft:player_attack");
                buffer.addEvent(ReplayBuffer.EVENT_CRIT, buffer.findTrack(FOE), ReplayBuffer.NO_TRACK, null);
            }
            if (tick == 52) {
                buffer.addEvent(ReplayBuffer.EVENT_TOTEM, buffer.findTrack(FOE), ReplayBuffer.NO_TRACK, null);
            }
            if (tick == 57) {
                buffer.addEvent(ReplayBuffer.EVENT_DEATH, buffer.findTrack(FOE), buffer.findTrack(ME), null);
            }
            buffer.beginTick();
            int me = buffer.trackFor(ME, "Steve", true);
            buffer.write(me, at(tick * 0.2, tick * 7F, tick));
            if (tick == 0) {
                buffer.setEquipment(me, 4, "minecraft:iron_sword");
            } else if (tick == 30) {
                buffer.setEquipment(me, 4, "minecraft:netherite_sword");
            } else if (tick == 48) {
                buffer.setEquipment(me, 5, "minecraft:totem_of_undying");
            } else if (tick == 55) {
                buffer.setEquipment(me, 5, EMPTY);
            }
            if (tick < 45 || tick > 49) {
                int foe = buffer.trackFor(FOE, "Notch", false);
                buffer.write(foe, at(3 - tick * 0.1, -tick * 3F, tick));
                if (tick == 50) {
                    buffer.setEquipment(foe, 0, "minecraft:netherite_helmet");
                }
            }
            if (tick >= 50) {
                int late = buffer.trackFor(LATE, "Jeb", false);
                buffer.write(late, at(10 + tick, 90F, tick));
            }
            if (tick < 10) {
                int gone = buffer.trackFor(GONE, "Dinnerbone", false);
                buffer.write(gone, at(-5, 0F, tick));
            }
        }
        return buffer;
    }

    private static ReplayRecording.Item encode(Object payload) {
        String id = (String) payload;
        return EMPTY.equals(id) ? null : new ReplayRecording.Item(id, 1, id.contains("netherite"), id.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void capturedWindowPlaysBackLikeTheLiveBuffer() throws IOException {
        ReplayBuffer buffer = live();
        long start = 25;
        long end = buffer.currentTick();
        long focus = 57;
        ReplayRecording rec = ReplayCapture.capture(buffer, start, end, focus, buffer.findTrack(FOE), buffer.findTrack(ME),
                ReplayCaptureTest::encode, (track, out) -> out.skinValue = "skin-" + buffer.name(track));
        assertNotNull(rec);
        assertEquals(35, rec.ticks);
        assertEquals(32, rec.focusTick);
        assertEquals(3, rec.tracks.size(), "Dinnerbone left before the window");
        assertEquals("Steve", rec.name(rec.killerTrack));
        assertEquals("Notch", rec.name(rec.victimTrack));
        assertEquals("skin-Jeb", rec.tracks.get(rec.tracks.size() - 1).skinValue);
        assertEquals(4, rec.events.size());
        assertEquals(4, rec.items.size(), "iron sword (held at the start), netherite sword, totem, helmet; the empty stack is no item");

        ReplayRecording decoded = ReplayCodec.decode(ReplayCodec.encode(ReplayCodecTest.header(ticks(rec)), rec)).recording();
        ReplayCodecTest.assertRecordingEquals(rec, decoded);

        ReplayCapture.Rebuilt rebuilt = ReplayCapture.rebuild(decoded, i -> decoded.items.get(i).id(), (track, source) -> source.name);
        ReplayBuffer copy = rebuilt.buffer();
        assertEquals(0, rebuilt.startTick());
        assertEquals(34, rebuilt.endTick());
        assertEquals(32, rebuilt.focusTick());
        assertEquals("Notch", copy.meta(rebuilt.victimTrack()));

        TrackSample original = new TrackSample();
        TrackSample replayed = new TrackSample();
        for (UUID uuid : new UUID[]{ME, FOE, LATE}) {
            int from = buffer.findTrack(uuid);
            int to = copy.findTrack(uuid);
            assertTrue(to >= 0, "rebuilt track for " + uuid);
            assertEquals(buffer.name(from), copy.name(to));
            assertEquals(buffer.isSelf(from), copy.isSelf(to));
            for (long tick = start; tick <= end; tick++) {
                long local = tick - start;
                assertEquals(buffer.has(from, tick), copy.has(to, local), buffer.name(from) + " presence at " + tick);
                if (buffer.read(from, tick, original)) {
                    assertTrue(copy.read(to, local, replayed));
                    ReplayCodecTest.assertSampleEquals(original, replayed);
                }
                for (int slot = 0; slot < ReplayBuffer.EQUIPMENT_SLOTS; slot++) {
                    Object live = buffer.equipmentAt(from, slot, tick);
                    Object shown = copy.equipmentAt(to, slot, local);
                    assertEquals(live == null || EMPTY.equals(live) ? null : live, shown, buffer.name(from) + " slot " + slot + " at " + tick);
                }
            }
            // Fractional sampling goes through the same interpolation.
            if (buffer.sample(from, 40.5, original)) {
                assertTrue(copy.sample(to, 40.5 - start, replayed));
                assertEquals(original.x, replayed.x, 1e-3);
                assertEquals(original.headYaw, replayed.headYaw, 1e-3);
            }
        }

        assertEquals(4, copy.eventCount());
        for (int i = 0; i < copy.eventCount(); i++) {
            ReplayRecording.Event e = rec.events.get(i);
            assertEquals(e.tick(), copy.eventTick(i));
            assertEquals(e.type(), copy.eventType(i));
            assertEquals(e.a(), copy.eventA(i));
            assertEquals(e.b() < 0 ? ReplayBuffer.NO_TRACK : e.b(), copy.eventB(i));
        }
        assertEquals(44 - start, copy.eventTick(0));
        assertEquals("minecraft:player_attack", copy.eventNote(0));
        assertEquals(copy.findTrack(ME), copy.eventB(0));
        assertNull(copy.eventNote(1));
    }

    @Test
    void windowIsClampedToWhatTheBufferHolds() {
        ReplayBuffer buffer = live();
        ReplayRecording rec = ReplayCapture.capture(buffer, 0, 1_000, -1, ReplayBuffer.NO_TRACK, ReplayBuffer.NO_TRACK,
                ReplayCaptureTest::encode, (track, out) -> { });
        assertNotNull(rec);
        assertEquals(40, rec.ticks, "buffer capacity");
        assertEquals(-1, rec.focusTick);
        assertEquals(-1, rec.killerTrack);
        assertEquals(Math.floor(100.3 + 20 * 0.2), rec.originX, "origin is my first position in the window");
        assertNull(ReplayCapture.capture(new ReplayBuffer(10, 1, 1), 0, 5, -1, -1, -1, ReplayCaptureTest::encode, (t, o) -> { }));
        assertNull(ReplayCapture.capture(buffer, 70, 80, -1, -1, -1, ReplayCaptureTest::encode, (t, o) -> { }));
    }

    @Test
    void identicalItemsAreStoredOnce() {
        ReplayBuffer buffer = new ReplayBuffer(20, 2, 4);
        for (int tick = 0; tick < 6; tick++) {
            buffer.beginTick();
            int a = buffer.trackFor(ME, "a", true);
            int b = buffer.trackFor(FOE, "b", false);
            buffer.write(a, at(tick, 0, tick));
            buffer.write(b, at(-tick, 0, tick));
            if (tick == 1) {
                buffer.setEquipment(a, 0, new String("minecraft:diamond_helmet"));
                buffer.setEquipment(b, 0, new String("minecraft:diamond_helmet"));
            }
        }
        ReplayRecording rec = ReplayCapture.capture(buffer, 0, 5, -1, -1, -1, ReplayCaptureTest::encode, (t, o) -> { });
        assertNotNull(rec);
        assertEquals(1, rec.items.size());
        assertEquals(0, rec.tracks.get(0).changes.getFirst().item());
        assertEquals(0, rec.tracks.get(1).changes.getFirst().item());
        assertFalse(rec.tracks.get(0).changes.isEmpty());
    }

    private static int ticks(ReplayRecording rec) {
        return rec.ticks;
    }
}
