package dev.skirmish.module.killcam.library;

import dev.skirmish.module.killcam.ReplayBuffer;
import dev.skirmish.module.killcam.TrackSample;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between the live {@link ReplayBuffer} and a {@link ReplayRecording}: {@link #capture} copies a window of
 * the buffer (client thread, the buffer keeps changing), {@link #rebuild} writes a recording back into a fresh buffer
 * tick by tick, so playback of a saved replay runs through the same sampling, interpolation and equipment lookups as
 * the live KillCam.
 */
public final class ReplayCapture {
    private ReplayCapture() {
    }

    /** Turns an equipment payload of the buffer into an item; null for an empty slot. */
    public interface ItemEncoder {
        ReplayRecording.@Nullable Item encode(Object payload);
    }

    /** Fills the per-player extras (model parts, main arm, skin) of a captured track. */
    public interface TrackStyle {
        void fill(int bufferTrack, ReplayRecording.Track out);
    }

    /** Payload for item {@code index} of the recording (decoded once per index by the caller). */
    public interface ItemDecoder {
        @Nullable Object decode(int index);
    }

    /** Meta object of a rebuilt track (the game's TrackMeta). */
    public interface MetaFactory {
        @Nullable Object meta(int track, ReplayRecording.Track source);
    }

    /** A rebuilt buffer: ticks {@code 0 .. endTick}; track numbers equal the recording's. */
    public record Rebuilt(ReplayBuffer buffer, long startTick, long endTick, long focusTick, int victimTrack, int killerTrack) {
    }

    /**
     * Copies ticks {@code [from, to]} (clamped to what the buffer holds) of every player with data in the window.
     * {@code focusTick}, {@code victim} and {@code killer} are buffer values (-1 / {@link ReplayBuffer#NO_TRACK} for
     * none). Returns null when the window is empty.
     */
    public static @Nullable ReplayRecording capture(ReplayBuffer buffer, long from, long to, long focusTick, int victim, int killer,
                                                    ItemEncoder items, TrackStyle style) {
        if (buffer.isEmpty()) {
            return null;
        }
        long start = Math.max(from, buffer.oldestTick());
        long end = Math.min(to, buffer.currentTick());
        if (end < start) {
            return null;
        }
        ReplayRecording rec = new ReplayRecording();
        rec.ticks = (int) (end - start + 1);
        rec.focusTick = focusTick >= start && focusTick <= end ? (int) (focusTick - start) : -1;
        ItemTable table = new ItemTable(rec, items);
        int[] map = new int[buffer.allocatedTracks()];
        java.util.Arrays.fill(map, -1);
        TrackSample sample = new TrackSample();
        for (int t = 0; t < buffer.allocatedTracks(); t++) {
            if (!buffer.isAssigned(t) || buffer.uuid(t) == null) {
                continue;
            }
            long first = buffer.firstPresent(t, start, end);
            if (first < 0) {
                continue;
            }
            ReplayRecording.Track track = new ReplayRecording.Track();
            track.uuid = buffer.uuid(t);
            track.name = buffer.name(t);
            track.self = buffer.isSelf(t);
            style.fill(t, track);
            for (long at = first; at <= end; at++) {
                if (buffer.read(t, at, sample)) {
                    TrackSample copy = new TrackSample();
                    copy.copyFrom(sample);
                    track.frames.add(new ReplayRecording.Frame((int) (at - start), copy));
                }
            }
            for (int slot = 0; slot < ReplayRecording.SLOTS; slot++) {
                track.baseItems[slot] = table.index(buffer.equipmentAt(t, slot, start));
            }
            for (int k = 0; k < buffer.equipmentChanges(t); k++) {
                long tick = buffer.equipmentChangeTick(t, k);
                if (tick > start && tick <= end) {
                    track.changes.add(new ReplayRecording.EquipmentChange((int) (tick - start), buffer.equipmentChangeSlot(t, k),
                            table.index(buffer.equipmentChangePayload(t, k))));
                }
            }
            track.changes.sort(Comparator.comparingInt(ReplayRecording.EquipmentChange::tick));
            map[t] = rec.tracks.size();
            rec.tracks.add(track);
        }
        rec.victimTrack = mapped(map, victim);
        rec.killerTrack = mapped(map, killer);
        for (int i = 0; i < buffer.eventCount(); i++) {
            long tick = buffer.eventTick(i);
            int a = mapped(map, buffer.eventA(i));
            if (tick < start || tick > end || a < 0) {
                continue;
            }
            Object note = buffer.eventNote(i);
            rec.events.add(new ReplayRecording.Event((int) (tick - start), buffer.eventType(i), a, mapped(map, buffer.eventB(i)),
                    note == null ? null : note.toString()));
        }
        rec.events.sort(Comparator.comparingInt(ReplayRecording.Event::tick));
        ReplayRecording.Track origin = null;
        for (ReplayRecording.Track track : rec.tracks) {
            if (!track.frames.isEmpty() && (origin == null || track.self)) {
                origin = track;
                if (track.self) {
                    break;
                }
            }
        }
        if (origin != null) {
            TrackSample s = origin.frames.getFirst().sample();
            rec.originX = Math.floor(s.x);
            rec.originY = Math.floor(s.y);
            rec.originZ = Math.floor(s.z);
        }
        return rec;
    }

    private static int mapped(int[] map, int bufferTrack) {
        return bufferTrack >= 0 && bufferTrack < map.length ? map[bufferTrack] : -1;
    }

    /** Distinct items: the same payload object is encoded once, equal encodings share one entry. */
    private static final class ItemTable {
        private final ReplayRecording rec;
        private final ItemEncoder encoder;
        private final Map<Object, Integer> byPayload = new IdentityHashMap<>();
        private final Map<ReplayRecording.Item, Integer> byContent = new HashMap<>();

        ItemTable(ReplayRecording rec, ItemEncoder encoder) {
            this.rec = rec;
            this.encoder = encoder;
        }

        int index(@Nullable Object payload) {
            if (payload == null) {
                return -1;
            }
            Integer known = byPayload.get(payload);
            if (known != null) {
                return known;
            }
            ReplayRecording.Item item = encoder.encode(payload);
            int index;
            if (item == null) {
                index = -1;
            } else {
                index = byContent.computeIfAbsent(item, k -> {
                    rec.items.add(k);
                    return rec.items.size() - 1;
                });
            }
            byPayload.put(payload, index);
            return index;
        }
    }

    /**
     * Writes the recording into a new buffer as if it were recorded live: all players get their track on tick 0
     * (in recording order, so track numbers stay the same), equipment and events are added at their ticks.
     */
    public static Rebuilt rebuild(ReplayRecording rec, ItemDecoder items, MetaFactory metas) {
        int trackCount = rec.tracks.size();
        List<ReplayRecording.Event> events = new ArrayList<>(rec.events);
        events.sort(Comparator.comparingInt(ReplayRecording.Event::tick));
        ReplayBuffer buffer = new ReplayBuffer(Math.max(2, rec.ticks), Math.max(1, trackCount), Math.max(1, events.size()));
        int[] frame = new int[trackCount];
        int[] change = new int[trackCount];
        int nextEvent = 0;
        for (int t = 0; t < rec.ticks; t++) {
            // Events are stamped with the next tick, so they go in before beginTick() of their tick.
            while (nextEvent < events.size() && events.get(nextEvent).tick() <= t) {
                ReplayRecording.Event e = events.get(nextEvent++);
                buffer.addEvent(e.type(), e.a(), e.b() < 0 ? ReplayBuffer.NO_TRACK : e.b(), e.note());
            }
            buffer.beginTick();
            if (t == 0) {
                for (int i = 0; i < trackCount; i++) {
                    ReplayRecording.Track track = rec.tracks.get(i);
                    int assigned = buffer.trackFor(track.uuid, track.name, track.self);
                    if (assigned != i) {
                        throw new IllegalStateException("track " + i + " rebuilt as " + assigned);
                    }
                    buffer.setMeta(i, metas.meta(i, track));
                    for (int slot = 0; slot < ReplayRecording.SLOTS; slot++) {
                        if (track.baseItems[slot] >= 0) {
                            buffer.setEquipment(i, slot, items.decode(track.baseItems[slot]));
                        }
                    }
                }
            }
            for (int i = 0; i < trackCount; i++) {
                ReplayRecording.Track track = rec.tracks.get(i);
                while (change[i] < track.changes.size() && track.changes.get(change[i]).tick() <= t) {
                    ReplayRecording.EquipmentChange c = track.changes.get(change[i]++);
                    buffer.setEquipment(i, c.slot(), c.item() < 0 ? null : items.decode(c.item()));
                }
                while (frame[i] < track.frames.size() && track.frames.get(frame[i]).tick() < t) {
                    frame[i]++;
                }
                if (frame[i] < track.frames.size() && track.frames.get(frame[i]).tick() == t) {
                    buffer.write(i, track.frames.get(frame[i]++).sample());
                }
            }
        }
        return new Rebuilt(buffer, 0, rec.ticks - 1, rec.focusTick, rec.victimTrack, rec.killerTrack);
    }
}
