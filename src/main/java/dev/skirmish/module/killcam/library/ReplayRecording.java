package dev.skirmish.module.killcam.library;

import dev.skirmish.module.killcam.ReplayBuffer;
import dev.skirmish.module.killcam.TrackSample;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The recorded data of a saved replay, without Minecraft types: per player the frames (tick + {@link TrackSample}),
 * the equipment at the first tick and its changes, plus the combat events. Ticks are relative to the start of the
 * recording ({@code 0 .. ticks - 1}); track numbers index {@link #tracks}; item numbers index {@link #items}
 * ({@code -1} = empty slot).
 */
public final class ReplayRecording {
    public static final int SLOTS = ReplayBuffer.EQUIPMENT_SLOTS;

    public int ticks;
    /** My death (DEATH), the kill (KILL), or -1. Drawn as the death marker on the timeline. */
    public int focusTick = -1;
    /** Who died at {@link #focusTick} (me for DEATH, the victim for KILL); -1 when not recorded. */
    public int victimTrack = -1;
    /** Who killed (the killer for DEATH, me for KILL and CLIP): the first-person camera. -1 when not recorded. */
    public int killerTrack = -1;
    /** Positions are stored relative to this point (my first recorded position). */
    public double originX;
    public double originY;
    public double originZ;
    public final List<Item> items = new ArrayList<>();
    public final List<Track> tracks = new ArrayList<>();
    public final List<Event> events = new ArrayList<>();

    /**
     * One distinct equipment stack. {@code data} is the serialized stack (may be empty); {@code id}, {@code count}
     * and {@code foil} are enough to show a plain copy when {@code data} cannot be read (e.g. another server's items).
     */
    public record Item(String id, int count, boolean foil, byte[] data) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Item other && id.equals(other.id) && count == other.count && foil == other.foil
                    && Arrays.equals(data, other.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, count, foil) * 31 + Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return id + " x" + count + (foil ? " (foil)" : "") + ", " + data.length + " B";
        }
    }

    /** One recorded state of a player. */
    public record Frame(int tick, TrackSample sample) {
    }

    public record EquipmentChange(int tick, int slot, int item) {
    }

    /** A combat event on track {@code a} (by track {@code b}, or -1), as {@link ReplayBuffer}'s event types. */
    public record Event(int tick, byte type, int a, int b, @Nullable String note) {
    }

    /** One recorded player. */
    public static final class Track {
        public UUID uuid = new UUID(0, 0);
        public String name = "";
        public boolean self;
        /** Player model parts byte (hat, jacket, sleeves...). */
        public byte modelParts = 0x7F;
        public boolean leftHanded;
        /** The profile's {@code textures} property, so the skin can be restored later; empty when unknown. */
        public String skinValue = "";
        public String skinSignature = "";
        /** Equipment at tick 0 per slot ({@code EquipmentSnapshot.SLOTS} order), -1 = empty. */
        public final int[] baseItems = filled(SLOTS, -1);
        public final List<EquipmentChange> changes = new ArrayList<>();
        public final List<Frame> frames = new ArrayList<>();

        @Override
        public String toString() {
            return name + (self ? " (me)" : "") + ": " + frames.size() + " frames, " + changes.size() + " equipment changes";
        }
    }

    private static int[] filled(int size, int value) {
        int[] a = new int[size];
        Arrays.fill(a, value);
        return a;
    }

    public int countEvents(byte type) {
        int n = 0;
        for (Event e : events) {
            if (e.type() == type) {
                n++;
            }
        }
        return n;
    }

    public int frameCount() {
        int n = 0;
        for (Track t : tracks) {
            n += t.frames.size();
        }
        return n;
    }

    /** Name of a track, or "" for -1 / out of range. */
    public String name(int track) {
        return track >= 0 && track < tracks.size() ? tracks.get(track).name : "";
    }
}
