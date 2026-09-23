package dev.skirmish.module.killcam;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Memory and recording cost of the buffer with the in-game sizes. Numbers are printed (see the test report's
 * standard output); the assertions are deliberately loose so a slow CI machine does not fail the build.
 */
class ReplayBufferBenchmarkTest {
    private static final int CAPACITY = Recorder.CAPACITY;

    @Test
    void memoryPerPlayer() {
        long perTrack = ReplayBuffer.bytesPerTrack(CAPACITY);
        System.out.printf(Locale.ROOT, "capacity %d ticks: %d B per player (%.1f B per player-tick incl. equipment ring)%n",
                CAPACITY, perTrack, (double) perTrack / CAPACITY);
        for (int players : new int[]{1, 10, 30}) {
            ReplayBuffer buffer = filled(players, CAPACITY);
            long bytes = buffer.memoryBytes();
            System.out.printf(Locale.ROOT, "%2d players: %,d B = %.1f KiB (formula), measured heap delta %s%n",
                    players, bytes, bytes / 1024.0, measuredDelta(players));
            assertEquals(players, buffer.allocatedTracks());
            assertTrue(bytes < players * 32_000L + 32_000L, "unexpectedly large: " + bytes);
        }
        assertTrue(perTrack < 32_000, "per player " + perTrack);
    }

    private static String measuredDelta(int players) {
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long before = runtime.totalMemory() - runtime.freeMemory();
        ReplayBuffer buffer = filled(players, CAPACITY);
        System.gc();
        long after = runtime.totalMemory() - runtime.freeMemory();
        String text = String.format(Locale.ROOT, "~%.1f KiB (noisy)", (after - before) / 1024.0);
        return buffer.allocatedTracks() == players ? text : text + "?";
    }

    private static ReplayBuffer filled(int players, int ticks) {
        ReplayBuffer buffer = new ReplayBuffer(CAPACITY, Recorder.MAX_TRACKS, Recorder.EVENT_CAPACITY);
        UUID[] ids = ids(players);
        TrackSample sample = new TrackSample();
        for (int tick = 0; tick < ticks; tick++) {
            buffer.beginTick();
            for (int p = 0; p < players; p++) {
                vary(sample, tick, p);
                buffer.write(buffer.trackFor(ids[p], "p" + p, p == 0), sample);
            }
        }
        return buffer;
    }

    @Test
    void recordTickCost() {
        int players = 30;
        int warmup = 5_000;
        int measured = 20_000;
        ReplayBuffer buffer = new ReplayBuffer(CAPACITY, Recorder.MAX_TRACKS, Recorder.EVENT_CAPACITY);
        UUID[] ids = ids(players);
        String[] names = new String[players];
        Object[] items = new Object[8];
        for (int i = 0; i < players; i++) {
            names[i] = "player" + i;
        }
        for (int i = 0; i < items.length; i++) {
            items[i] = "item" + i;
        }
        TrackSample sample = new TrackSample();
        for (int tick = 0; tick < warmup; tick++) {
            recordTick(buffer, ids, names, items, sample, tick);
        }
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long allocatedBefore = threads.getCurrentThreadAllocatedBytes();
        long total = 0;
        long max = 0;
        for (int tick = warmup; tick < warmup + measured; tick++) {
            long start = System.nanoTime();
            recordTick(buffer, ids, names, items, sample, tick);
            long elapsed = System.nanoTime() - start;
            total += elapsed;
            max = Math.max(max, elapsed);
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - allocatedBefore;
        double avgMicros = total / 1000.0 / measured;
        System.out.printf(Locale.ROOT, "record tick, %d players: avg %.2f us, max %.1f us, %.3f us per player; allocated %d B over %d ticks (%.2f B/tick)%n",
                players, avgMicros, max / 1000.0, avgMicros / players, allocated, measured, (double) allocated / measured);
        assertTrue(avgMicros < 1_000, "avg " + avgMicros + " us");
        assertTrue(allocated < 1_000_000, "recording allocates: " + allocated + " B");
    }

    private static void recordTick(ReplayBuffer buffer, UUID[] ids, String[] names, Object[] items, TrackSample sample, int tick) {
        buffer.beginTick();
        for (int p = 0; p < ids.length; p++) {
            int track = buffer.trackFor(ids[p], names[p], p == 0);
            vary(sample, tick, p);
            buffer.write(track, sample);
            if ((tick + p) % 40 == 0) {
                buffer.setEquipment(track, 4, items[(tick / 40 + p) % items.length]);
            }
        }
        if (tick % 10 == 0) {
            buffer.addEvent(ReplayBuffer.EVENT_HIT, tick % ids.length, (tick + 1) % ids.length, "minecraft:player_attack");
        }
    }

    private static void vary(TrackSample s, int tick, int player) {
        s.x = player * 3 + tick * 0.1;
        s.y = 64;
        s.z = player - tick * 0.05;
        s.headYaw = tick * 3 % 360;
        s.bodyYaw = s.headYaw - 10;
        s.pitch = (tick % 60) - 30;
        s.walkPos = tick * 0.3F;
        s.walkSpeed = 0.5F;
        s.attackAnim = (tick % 6) / 6.0F;
        s.health = 20 - (tick % 20);
        s.pose = (byte) (tick % 50 == 0 ? 5 : 0);
        s.hurtTime = (byte) (tick % 10);
        s.anim = (byte) (tick & 7);
    }

    private static UUID[] ids(int players) {
        UUID[] ids = new UUID[players];
        for (int i = 0; i < players; i++) {
            ids[i] = new UUID(0x5EED, i);
        }
        return ids;
    }
}
