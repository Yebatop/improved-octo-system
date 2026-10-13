package dev.skirmish.module.killcam;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ring buffer of the last {@code capacity} ticks for up to {@code maxTracks} players, without Minecraft types.
 *
 * <p>Every player gets a track (primitive arrays of {@code capacity} entries, allocated when the track is first
 * used and reused afterwards). Slot {@code tick % capacity} holds the state of that tick; a per-slot stamp tells
 * whether the slot belongs to the requested tick, so wrap-around and gaps (player out of range) need no clearing.
 * Equipment is not stored per tick: {@link #setEquipment} appends a change event (tick, slot, payload) to a small
 * per-track ring, and {@link #equipmentAt} finds the last change at or before a tick. Payloads are opaque objects
 * (ItemStack copies in game, strings in tests). Combat events live in one global ring.
 *
 * <p>Recording ({@link #beginTick}, {@link #trackFor} for a known player, {@link #write}) does not allocate.
 */
public final class ReplayBuffer {
    public static final int NO_TRACK = -1;
    public static final int EQUIPMENT_SLOTS = 6;
    public static final int EQUIPMENT_EVENTS = 64;
    /** Positions further apart than this between two ticks are a teleport and are not interpolated. */
    public static final double TELEPORT_DISTANCE = 8.0;

    public static final byte EVENT_HIT = 1;
    public static final byte EVENT_CRIT = 2;
    public static final byte EVENT_MAGIC_CRIT = 3;
    public static final byte EVENT_TOTEM = 4;
    public static final byte EVENT_DEATH = 5;

    /** Assumed reference size (compressed oops) and array header for {@link #memoryBytes()}. */
    static final int REF_BYTES = 4;
    static final int ARRAY_HEADER = 16;
    static final int OBJECT_HEADER = 16;

    private final int capacity;
    private final int maxTracks;
    private final Track[] tracks;
    private int allocated;
    private final Map<UUID, Integer> byUuid = new HashMap<>();
    private long tick = -1;
    private long validFrom;

    private final int eventCapacity;
    private final long[] evTick;
    private final byte[] evType;
    private final int[] evA;
    private final int[] evB;
    private final Object[] evNote;
    private int evStart;
    private int evCount;
    private long equipmentOverflows;

    public ReplayBuffer(int capacity, int maxTracks, int eventCapacity) {
        if (capacity < 2 || maxTracks < 1 || eventCapacity < 1) {
            throw new IllegalArgumentException("capacity=" + capacity + " maxTracks=" + maxTracks + " events=" + eventCapacity);
        }
        this.capacity = capacity;
        this.maxTracks = maxTracks;
        this.tracks = new Track[maxTracks];
        this.eventCapacity = eventCapacity;
        this.evTick = new long[eventCapacity];
        this.evType = new byte[eventCapacity];
        this.evA = new int[eventCapacity];
        this.evB = new int[eventCapacity];
        this.evNote = new Object[eventCapacity];
    }

    // ---- ticks ----

    /** Starts recording a new tick and returns its index. */
    public long beginTick() {
        return ++tick;
    }

    /** Index of the last begun tick (-1 before the first). */
    public long currentTick() {
        return tick;
    }

    /** Oldest tick whose data is still in the buffer. */
    public long oldestTick() {
        return Math.max(validFrom, tick - capacity + 1);
    }

    public boolean isEmpty() {
        return tick < validFrom;
    }

    public int capacity() {
        return capacity;
    }

    public int maxTracks() {
        return maxTracks;
    }

    /** Forgets all data, tracks, equipment and events; array allocations are kept for reuse. */
    public void clear() {
        validFrom = tick + 1;
        byUuid.clear();
        for (int i = 0; i < allocated; i++) {
            tracks[i].release();
        }
        java.util.Arrays.fill(evNote, null);
        evStart = 0;
        evCount = 0;
    }

    // ---- tracks ----

    /**
     * Track of {@code uuid}, assigning one (a never used, released or expired track, or a new allocation) when the
     * player has none. Returns {@link #NO_TRACK} when all {@code maxTracks} tracks hold data inside the window.
     */
    public int trackFor(UUID uuid, String name, boolean self) {
        Integer existing = byUuid.get(uuid);
        if (existing != null) {
            return existing;
        }
        long oldest = oldestTick();
        Track chosen = null;
        for (int i = 0; i < allocated; i++) {
            Track track = tracks[i];
            if (track.uuid == null || track.lastTick < oldest) {
                chosen = track;
                break;
            }
        }
        if (chosen == null) {
            if (allocated == maxTracks) {
                return NO_TRACK;
            }
            chosen = new Track(allocated, capacity);
            tracks[allocated++] = chosen;
        }
        if (chosen.uuid != null) {
            byUuid.remove(chosen.uuid);
        }
        chosen.assign(uuid, name, self, tick);
        byUuid.put(uuid, chosen.index);
        return chosen.index;
    }

    /** Track currently assigned to {@code uuid}, or {@link #NO_TRACK}. */
    public int findTrack(UUID uuid) {
        Integer existing = byUuid.get(uuid);
        return existing == null ? NO_TRACK : existing;
    }

    /** Number of track slots allocated so far (assigned or free). */
    public int allocatedTracks() {
        return allocated;
    }

    public int assignedTracks() {
        return byUuid.size();
    }

    public boolean isAssigned(int track) {
        return track >= 0 && track < allocated && tracks[track].uuid != null;
    }

    public @Nullable UUID uuid(int track) {
        return tracks[track].uuid;
    }

    public String name(int track) {
        return tracks[track].name;
    }

    public boolean isSelf(int track) {
        return tracks[track].self;
    }

    /** Incremented on every (re)assignment of the track to a player. */
    public int generation(int track) {
        return tracks[track].generation;
    }

    public void setMeta(int track, @Nullable Object meta) {
        tracks[track].meta = meta;
    }

    public @Nullable Object meta(int track) {
        return tracks[track].meta;
    }

    /** Last tick written for the track, or the assignment tick when nothing was written yet. */
    public long lastTick(int track) {
        return tracks[track].lastTick;
    }

    // ---- samples ----

    /** Stores the state of the track in the current tick. */
    public void write(int track, TrackSample s) {
        Track t = tracks[track];
        int i = slot(tick);
        t.stamp[i] = (int) tick;
        t.x[i] = s.x;
        t.y[i] = s.y;
        t.z[i] = s.z;
        t.headYaw[i] = s.headYaw;
        t.bodyYaw[i] = s.bodyYaw;
        t.pitch[i] = s.pitch;
        t.walkPos[i] = s.walkPos;
        t.walkSpeed[i] = s.walkSpeed;
        t.attackAnim[i] = s.attackAnim;
        t.health[i] = s.health;
        t.absorption[i] = s.absorption;
        t.sharedFlags[i] = s.sharedFlags;
        t.livingFlags[i] = s.livingFlags;
        t.pose[i] = s.pose;
        t.hurtTime[i] = s.hurtTime;
        t.deathTime[i] = s.deathTime;
        t.anim[i] = s.anim;
        t.useTicks[i] = s.useTicks;
        t.lastTick = tick;
    }

    public boolean has(int track, long at) {
        if (track < 0 || track >= allocated || at < oldestTick() || at > tick) {
            return false;
        }
        Track t = tracks[track];
        return t.uuid != null && at >= t.assignedTick && t.stamp[slot(at)] == (int) at;
    }

    /** Exact state of one tick. */
    public boolean read(int track, long at, TrackSample out) {
        if (!has(track, at)) {
            return false;
        }
        Track t = tracks[track];
        int i = slot(at);
        out.x = t.x[i];
        out.y = t.y[i];
        out.z = t.z[i];
        out.headYaw = t.headYaw[i];
        out.bodyYaw = t.bodyYaw[i];
        out.pitch = t.pitch[i];
        out.walkPos = t.walkPos[i];
        out.walkSpeed = t.walkSpeed[i];
        out.attackAnim = t.attackAnim[i];
        out.health = t.health[i];
        out.absorption = t.absorption[i];
        out.sharedFlags = t.sharedFlags[i];
        out.livingFlags = t.livingFlags[i];
        out.pose = t.pose[i];
        out.hurtTime = t.hurtTime[i];
        out.deathTime = t.deathTime[i];
        out.anim = t.anim[i];
        out.useTicks = t.useTicks[i];
        return true;
    }

    /**
     * State at a fractional tick: continuous values are interpolated between {@code floor(at)} and the next tick
     * (angles along the short way, arm swing like {@code LivingEntity.getAttackAnim}), discrete values come from
     * {@code floor(at)}. False when the player has no data at {@code floor(at)}.
     */
    public boolean sample(int track, double at, TrackSample out) {
        long i0 = (long) Math.floor(at);
        if (!read(track, i0, out)) {
            return false;
        }
        double frac = at - i0;
        if (frac <= 0 || !has(track, i0 + 1)) {
            return true;
        }
        Track t = tracks[track];
        int j = slot(i0 + 1);
        float f = (float) frac;
        double dx = t.x[j] - out.x;
        double dy = t.y[j] - out.y;
        double dz = t.z[j] - out.z;
        if (dx * dx + dy * dy + dz * dz <= TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
            out.x += dx * frac;
            out.y += dy * frac;
            out.z += dz * frac;
        }
        out.headYaw = rotLerp(out.headYaw, t.headYaw[j], f);
        out.bodyYaw = rotLerp(out.bodyYaw, t.bodyYaw[j], f);
        out.pitch += (t.pitch[j] - out.pitch) * f;
        out.walkPos += (t.walkPos[j] - out.walkPos) * f;
        out.walkSpeed += (t.walkSpeed[j] - out.walkSpeed) * f;
        out.health += (t.health[j] - out.health) * f;
        out.absorption += (t.absorption[j] - out.absorption) * f;
        float swing = t.attackAnim[j] - out.attackAnim;
        if (swing < 0) {
            swing += 1.0F;
        }
        out.attackAnim += swing * f;
        return true;
    }

    /** First tick in [from, to] with data for the track, or -1. */
    public long firstPresent(int track, long from, long to) {
        for (long at = Math.max(from, oldestTick()); at <= to; at++) {
            if (has(track, at)) {
                return at;
            }
        }
        return -1;
    }

    /** Last tick in [from, to] (scanning down from {@code from}) with data for the track, or -1. */
    public long lastPresent(int track, long from, long to) {
        for (long at = Math.min(from, tick); at >= Math.max(to, oldestTick()); at--) {
            if (has(track, at)) {
                return at;
            }
        }
        return -1;
    }

    // ---- equipment ----

    /** Records an equipment change of {@code slot} at the current tick. */
    public void setEquipment(int track, int slot, @Nullable Object payload) {
        Track t = tracks[track];
        if (t.eqCount == EQUIPMENT_EVENTS) {
            int oldest = t.eqStart;
            t.eqBase[t.eqSlot[oldest]] = t.eqPayload[oldest];
            t.eqPayload[oldest] = null;
            t.eqStart = (t.eqStart + 1) % EQUIPMENT_EVENTS;
            t.eqCount--;
            if (t.eqTick[oldest] >= (int) oldestTick()) {
                equipmentOverflows++;
            }
        }
        int i = (t.eqStart + t.eqCount) % EQUIPMENT_EVENTS;
        t.eqTick[i] = (int) tick;
        t.eqSlot[i] = (byte) slot;
        t.eqPayload[i] = payload;
        t.eqCount++;
        t.eqLatest[slot] = payload;
    }

    /** Payload of the last change of {@code slot} at or before {@code at}. */
    public @Nullable Object equipmentAt(int track, int slot, long at) {
        Track t = tracks[track];
        for (int k = t.eqCount - 1; k >= 0; k--) {
            int i = (t.eqStart + k) % EQUIPMENT_EVENTS;
            if (t.eqSlot[i] == slot && t.eqTick[i] <= at) {
                return t.eqPayload[i];
            }
        }
        return t.eqBase[slot];
    }

    /** Payload of the newest change of {@code slot} (what the recorder compares against). */
    public @Nullable Object latestEquipment(int track, int slot) {
        return tracks[track].eqLatest[slot];
    }

    /** Equipment changes currently stored for the track. */
    public int equipmentChanges(int track) {
        return tracks[track].eqCount;
    }

    /** Tick of the {@code k}-th stored equipment change of the track (0 = oldest, up to {@link #equipmentChanges}). */
    public long equipmentChangeTick(int track, int k) {
        return tracks[track].eqTick[equipmentChange(track, k)];
    }

    public int equipmentChangeSlot(int track, int k) {
        return tracks[track].eqSlot[equipmentChange(track, k)];
    }

    public @Nullable Object equipmentChangePayload(int track, int k) {
        return tracks[track].eqPayload[equipmentChange(track, k)];
    }

    private int equipmentChange(int track, int k) {
        Track t = tracks[track];
        if (k < 0 || k >= t.eqCount) {
            throw new IndexOutOfBoundsException(k);
        }
        return (t.eqStart + k) % EQUIPMENT_EVENTS;
    }

    /** Stored equipment payloads over all tracks (change events plus base states). */
    public int equipmentPayloads() {
        int count = 0;
        for (int i = 0; i < allocated; i++) {
            Track t = tracks[i];
            count += t.eqCount;
            for (Object base : t.eqBase) {
                if (base != null) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Changes evicted while still inside the window (more than {@link #EQUIPMENT_EVENTS} changes in the window). */
    public long equipmentOverflows() {
        return equipmentOverflows;
    }

    // ---- events ----

    /**
     * Adds a combat event. Events arrive between ticks (packets are handled before the end-of-tick recording), so
     * they are stamped with the tick that is recorded next.
     */
    public void addEvent(byte type, int trackA, int trackB, @Nullable Object note) {
        int i;
        if (evCount == eventCapacity) {
            i = evStart;
            evStart = (evStart + 1) % eventCapacity;
        } else {
            i = (evStart + evCount) % eventCapacity;
            evCount++;
        }
        evTick[i] = tick + 1;
        evType[i] = type;
        evA[i] = trackA;
        evB[i] = trackB;
        evNote[i] = note;
    }

    /** Events in the ring, oldest first; index them with 0..eventCount()-1. Older ones may be outside the window. */
    public int eventCount() {
        return evCount;
    }

    public long eventTick(int index) {
        return evTick[event(index)];
    }

    public byte eventType(int index) {
        return evType[event(index)];
    }

    public int eventA(int index) {
        return evA[event(index)];
    }

    public int eventB(int index) {
        return evB[event(index)];
    }

    public @Nullable Object eventNote(int index) {
        return evNote[event(index)];
    }

    private int event(int index) {
        if (index < 0 || index >= evCount) {
            throw new IndexOutOfBoundsException(index);
        }
        return (evStart + index) % eventCapacity;
    }

    // ---- memory ----

    /** Bytes of the primitive and reference arrays of one track (payload objects not included). */
    public static long bytesPerTrack(int capacity) {
        long perTick = 4 + 3 * 8 + 8 * 4 + 6 + 2;
        long arrays = 18;
        long samples = perTick * capacity + arrays * ARRAY_HEADER;
        long equipment = EQUIPMENT_EVENTS * (4 + 1 + REF_BYTES) + 2L * EQUIPMENT_SLOTS * REF_BYTES + 5L * ARRAY_HEADER;
        return samples + equipment + OBJECT_HEADER + 64;
    }

    /** Estimated retained size of the buffer's arrays (allocated tracks and event ring). */
    public long memoryBytes() {
        long events = (long) eventCapacity * (8 + 1 + 4 + 4 + REF_BYTES) + 5L * ARRAY_HEADER;
        return allocated * bytesPerTrack(capacity) + events + (long) maxTracks * REF_BYTES + ARRAY_HEADER + OBJECT_HEADER;
    }

    private int slot(long at) {
        return (int) Math.floorMod(at, (long) capacity);
    }

    static float rotLerp(float from, float to, float t) {
        return from + wrapDegrees(to - from) * t;
    }

    static float wrapDegrees(float degrees) {
        float f = degrees % 360.0F;
        if (f >= 180.0F) {
            f -= 360.0F;
        }
        if (f < -180.0F) {
            f += 360.0F;
        }
        return f;
    }

    private static final class Track {
        final int index;
        @Nullable UUID uuid;
        String name = "";
        boolean self;
        @Nullable Object meta;
        int generation;
        long assignedTick;
        long lastTick = -1;

        final int[] stamp;
        final double[] x;
        final double[] y;
        final double[] z;
        final float[] headYaw;
        final float[] bodyYaw;
        final float[] pitch;
        final float[] walkPos;
        final float[] walkSpeed;
        final float[] attackAnim;
        final float[] health;
        final float[] absorption;
        final byte[] sharedFlags;
        final byte[] livingFlags;
        final byte[] pose;
        final byte[] hurtTime;
        final byte[] deathTime;
        final byte[] anim;
        final short[] useTicks;

        final int[] eqTick = new int[EQUIPMENT_EVENTS];
        final byte[] eqSlot = new byte[EQUIPMENT_EVENTS];
        final Object[] eqPayload = new Object[EQUIPMENT_EVENTS];
        final Object[] eqBase = new Object[EQUIPMENT_SLOTS];
        final Object[] eqLatest = new Object[EQUIPMENT_SLOTS];
        int eqStart;
        int eqCount;

        Track(int index, int capacity) {
            this.index = index;
            stamp = new int[capacity];
            java.util.Arrays.fill(stamp, Integer.MIN_VALUE);
            x = new double[capacity];
            y = new double[capacity];
            z = new double[capacity];
            headYaw = new float[capacity];
            bodyYaw = new float[capacity];
            pitch = new float[capacity];
            walkPos = new float[capacity];
            walkSpeed = new float[capacity];
            attackAnim = new float[capacity];
            health = new float[capacity];
            absorption = new float[capacity];
            sharedFlags = new byte[capacity];
            livingFlags = new byte[capacity];
            pose = new byte[capacity];
            hurtTime = new byte[capacity];
            deathTime = new byte[capacity];
            anim = new byte[capacity];
            useTicks = new short[capacity];
        }

        void assign(UUID uuid, String name, boolean self, long tick) {
            clearEquipment();
            this.uuid = uuid;
            this.name = name;
            this.self = self;
            this.meta = null;
            this.generation++;
            this.assignedTick = tick;
            this.lastTick = tick;
        }

        void release() {
            clearEquipment();
            uuid = null;
            meta = null;
            lastTick = -1;
        }

        private void clearEquipment() {
            java.util.Arrays.fill(eqPayload, null);
            java.util.Arrays.fill(eqBase, null);
            java.util.Arrays.fill(eqLatest, null);
            eqStart = 0;
            eqCount = 0;
        }
    }
}
