package dev.skirmish.module.killcam.library;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.skirmish.module.killcam.TrackSample;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Binary replay files ({@code *.skreplay}).
 *
 * <pre>
 * "SKRP"            magic
 * u16               format version ({@link #VERSION})
 * u32 + bytes       header: UTF-8 JSON ({@link ReplayHeader}), readable without the rest
 * u32               compressed body length
 * u32               CRC-32 of the compressed body
 * bytes             body, DEFLATE
 * </pre>
 *
 * Body v1 (varints are unsigned LEB128, "zig" = zigzag): ticks, focus+1, victim+1, killer+1, origin (3 doubles);
 * items (id, count, foil, serialized stack); tracks (uuid, name, flags, model parts, skin property, 6 base items,
 * equipment changes, frames); events. A frame is the tick gap, the position as zig deltas in 1/4096 block from the
 * previous frame (the first from the origin), eight floats (head/body yaw, pitch, walk position/speed, swing, health,
 * absorption), six bytes (flags, pose, hurt/death time, anim) and the use ticks.
 *
 * <p>Readers check every length and index; anything out of place is a {@link ReplayFormatException}, never a crash.
 * A newer format version is refused (the header may have changed meaning); older versions below
 * {@link #MIN_VERSION} are refused, versions in between are decoded by their own body reader.
 */
public final class ReplayCodec {
    public static final String EXTENSION = ".skreplay";
    public static final int VERSION = 1;
    public static final int MIN_VERSION = 1;
    /** Position resolution: 1/4096 block. */
    public static final double POSITION_SCALE = 4096.0;

    static final byte[] MAGIC = {'S', 'K', 'R', 'P'};
    static final int MAX_HEADER_BYTES = 64 * 1024;
    static final int MAX_BODY_BYTES = 16 << 20;
    static final long MAX_RAW_BYTES = 32L << 20;
    static final int MAX_TICKS = 72_000;
    static final int MAX_TRACKS = 1024;
    static final int MAX_ITEMS = 65_536;
    static final int MAX_CHANGES = 65_536;
    static final int MAX_EVENTS = 1_000_000;
    static final int MAX_ITEM_BYTES = 1 << 20;
    static final int MAX_STRING_BYTES = 32 * 1024;

    private ReplayCodec() {
    }

    public record Decoded(ReplayHeader header, ReplayRecording recording) {
    }

    // ---- whole files ----

    public static byte[] encode(ReplayHeader header, ReplayRecording recording) throws IOException {
        return assemble(header, compress(recording));
    }

    public static Decoded decode(byte[] file) throws IOException {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(file));
            int version = readPrefix(in);
            ReplayHeader header = readHeaderJson(in);
            byte[] body = readBody(in);
            ReplayRecording recording = switch (version) {
                case 1 -> readBodyV1(body);
                default -> throw new ReplayFormatException(ReplayFormatException.Problem.OLD_VERSION, "no reader for format v" + version);
            };
            if (recording.ticks != header.ticks) {
                throw new ReplayFormatException("header says " + header.ticks + " ticks, body has " + recording.ticks);
            }
            return new Decoded(header, recording);
        } catch (EOFException e) {
            throw new ReplayFormatException("file is truncated");
        }
    }

    /** Reads the header only (the prefix and the JSON); the body is not touched. */
    public static ReplayHeader readHeader(InputStream stream) throws IOException {
        try {
            DataInputStream in = new DataInputStream(stream);
            readPrefix(in);
            return readHeaderJson(in);
        } catch (EOFException e) {
            throw new ReplayFormatException("file is truncated");
        }
    }

    /** The same file with another header (rename, star); the compressed body is kept byte for byte. */
    public static byte[] withHeader(byte[] file, ReplayHeader header) throws IOException {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(file));
            int version = readPrefix(in);
            readHeaderJson(in);
            byte[] body = readBody(in);
            if (version == VERSION) {
                return assemble(header, body);
            }
            return encode(header, decode(file).recording());
        } catch (EOFException e) {
            throw new ReplayFormatException("file is truncated");
        }
    }

    static byte[] assemble(ReplayHeader header, byte[] body) throws IOException {
        byte[] json = header.toJson().toString().getBytes(StandardCharsets.UTF_8);
        if (json.length > MAX_HEADER_BYTES) {
            throw new IOException("header too large: " + json.length + " B");
        }
        CRC32 crc = new CRC32();
        crc.update(body);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(body.length + json.length + 32);
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(MAGIC);
        out.writeShort(VERSION);
        out.writeInt(json.length);
        out.write(json);
        out.writeInt(body.length);
        out.writeInt((int) crc.getValue());
        out.write(body);
        out.flush();
        return bytes.toByteArray();
    }

    private static int readPrefix(DataInputStream in) throws IOException {
        byte[] magic = new byte[MAGIC.length];
        in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new ReplayFormatException(ReplayFormatException.Problem.NOT_A_REPLAY, "not a Skirmish replay");
        }
        int version = in.readUnsignedShort();
        if (version > VERSION) {
            throw new ReplayFormatException(ReplayFormatException.Problem.NEWER_VERSION,
                    "format v" + version + " is newer than this version of Skirmish reads (v" + VERSION + ")");
        }
        if (version < MIN_VERSION) {
            throw new ReplayFormatException(ReplayFormatException.Problem.OLD_VERSION,
                    "old format v" + version + " is no longer supported (v" + MIN_VERSION + " and newer)");
        }
        return version;
    }

    private static ReplayHeader readHeaderJson(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_HEADER_BYTES) {
            throw new ReplayFormatException("bad header length " + length);
        }
        byte[] json = new byte[length];
        in.readFully(json);
        JsonElement root;
        try {
            root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new ReplayFormatException("header is not JSON: " + e.getMessage());
        }
        if (!root.isJsonObject()) {
            throw new ReplayFormatException("header is not a JSON object");
        }
        return ReplayHeader.fromJson(root.getAsJsonObject());
    }

    private static byte[] readBody(DataInputStream in) throws IOException {
        int length = in.readInt();
        int expected = in.readInt();
        if (length <= 0 || length > MAX_BODY_BYTES) {
            throw new ReplayFormatException("bad body length " + length);
        }
        byte[] body = new byte[length];
        in.readFully(body);
        CRC32 crc = new CRC32();
        crc.update(body);
        if ((int) crc.getValue() != expected) {
            throw new ReplayFormatException("checksum mismatch: the file is damaged");
        }
        return body;
    }

    // ---- body v1 ----

    static byte[] compress(ReplayRecording rec) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096 + rec.frameCount() * 24);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes, deflater, 8192))) {
            writeBodyV1(out, rec);
        } finally {
            deflater.end();
        }
        return bytes.toByteArray();
    }

    private static void writeBodyV1(DataOutputStream out, ReplayRecording rec) throws IOException {
        varint(out, rec.ticks);
        varint(out, rec.focusTick + 1);
        varint(out, rec.victimTrack + 1);
        varint(out, rec.killerTrack + 1);
        out.writeDouble(rec.originX);
        out.writeDouble(rec.originY);
        out.writeDouble(rec.originZ);

        varint(out, rec.items.size());
        for (ReplayRecording.Item item : rec.items) {
            string(out, item.id());
            varint(out, Math.max(0, item.count()));
            out.writeBoolean(item.foil());
            varint(out, item.data().length);
            out.write(item.data());
        }

        varint(out, rec.tracks.size());
        for (ReplayRecording.Track track : rec.tracks) {
            out.writeLong(track.uuid.getMostSignificantBits());
            out.writeLong(track.uuid.getLeastSignificantBits());
            string(out, track.name);
            out.writeByte((track.self ? 1 : 0) | (track.leftHanded ? 2 : 0));
            out.writeByte(track.modelParts);
            string(out, track.skinValue);
            string(out, track.skinSignature);
            for (int slot = 0; slot < ReplayRecording.SLOTS; slot++) {
                varint(out, track.baseItems[slot] + 1);
            }
            varint(out, track.changes.size());
            int previousChange = 0;
            for (ReplayRecording.EquipmentChange change : track.changes) {
                zig(out, change.tick() - previousChange);
                previousChange = change.tick();
                out.writeByte(change.slot());
                varint(out, change.item() + 1);
            }
            varint(out, track.frames.size());
            int previousTick = -1;
            long qx = 0;
            long qy = 0;
            long qz = 0;
            for (ReplayRecording.Frame frame : track.frames) {
                TrackSample s = frame.sample();
                if (frame.tick() <= previousTick) {
                    throw new IllegalArgumentException("frames of " + track.name + " are not in tick order");
                }
                varint(out, frame.tick() - previousTick - 1);
                previousTick = frame.tick();
                long nx = quantize(s.x - rec.originX);
                long ny = quantize(s.y - rec.originY);
                long nz = quantize(s.z - rec.originZ);
                zig(out, nx - qx);
                zig(out, ny - qy);
                zig(out, nz - qz);
                qx = nx;
                qy = ny;
                qz = nz;
                out.writeFloat(s.headYaw);
                out.writeFloat(s.bodyYaw);
                out.writeFloat(s.pitch);
                out.writeFloat(s.walkPos);
                out.writeFloat(s.walkSpeed);
                out.writeFloat(s.attackAnim);
                out.writeFloat(s.health);
                out.writeFloat(s.absorption);
                out.writeByte(s.sharedFlags);
                out.writeByte(s.livingFlags);
                out.writeByte(s.pose);
                out.writeByte(s.hurtTime);
                out.writeByte(s.deathTime);
                out.writeByte(s.anim);
                zig(out, s.useTicks);
            }
        }

        varint(out, rec.events.size());
        int previousEvent = 0;
        for (ReplayRecording.Event event : rec.events) {
            zig(out, event.tick() - previousEvent);
            previousEvent = event.tick();
            out.writeByte(event.type());
            varint(out, event.a() + 1);
            varint(out, event.b() + 1);
            out.writeBoolean(event.note() != null);
            if (event.note() != null) {
                string(out, event.note());
            }
        }
    }

    private static ReplayRecording readBodyV1(byte[] body) throws IOException {
        Inflater inflater = new Inflater();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new Limited(new InflaterInputStream(new ByteArrayInputStream(body), inflater), MAX_RAW_BYTES)))) {
            return readBodyV1(in);
        } catch (EOFException e) {
            throw new ReplayFormatException("body is truncated");
        } catch (java.util.zip.ZipException e) {
            throw new ReplayFormatException("body cannot be decompressed: " + e.getMessage());
        } finally {
            inflater.end();
        }
    }

    private static ReplayRecording readBodyV1(DataInputStream in) throws IOException {
        ReplayRecording rec = new ReplayRecording();
        rec.ticks = count(in, 1, MAX_TICKS, "ticks");
        rec.focusTick = count(in, 0, rec.ticks, "focus tick") - 1;
        int victim = varint(in) - 1;
        int killer = varint(in) - 1;
        rec.originX = finite(in.readDouble());
        rec.originY = finite(in.readDouble());
        rec.originZ = finite(in.readDouble());

        int items = count(in, 0, MAX_ITEMS, "items");
        for (int i = 0; i < items; i++) {
            String id = string(in);
            int count = varint(in);
            boolean foil = in.readBoolean();
            byte[] data = new byte[count(in, 0, MAX_ITEM_BYTES, "item data")];
            in.readFully(data);
            rec.items.add(new ReplayRecording.Item(id, count, foil, data));
        }

        int tracks = count(in, 0, MAX_TRACKS, "tracks");
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < tracks; i++) {
            ReplayRecording.Track track = new ReplayRecording.Track();
            track.uuid = new UUID(in.readLong(), in.readLong());
            if (!seen.add(track.uuid)) {
                throw new ReplayFormatException("player " + track.uuid + " recorded twice");
            }
            track.name = string(in);
            int flags = in.readUnsignedByte();
            track.self = (flags & 1) != 0;
            track.leftHanded = (flags & 2) != 0;
            track.modelParts = in.readByte();
            track.skinValue = string(in);
            track.skinSignature = string(in);
            for (int slot = 0; slot < ReplayRecording.SLOTS; slot++) {
                track.baseItems[slot] = itemIndex(in, items);
            }
            int changes = count(in, 0, MAX_CHANGES, "equipment changes");
            long tick = 0;
            for (int c = 0; c < changes; c++) {
                tick += zig(in);
                int slot = in.readUnsignedByte();
                if (tick < 0 || tick >= rec.ticks || slot >= ReplayRecording.SLOTS) {
                    throw new ReplayFormatException("bad equipment change: tick " + tick + ", slot " + slot);
                }
                track.changes.add(new ReplayRecording.EquipmentChange((int) tick, slot, itemIndex(in, items)));
            }
            int frames = count(in, 0, rec.ticks, "frames");
            int previous = -1;
            long qx = 0;
            long qy = 0;
            long qz = 0;
            for (int f = 0; f < frames; f++) {
                int at = previous + 1 + varint(in);
                if (at >= rec.ticks) {
                    throw new ReplayFormatException("frame at tick " + at + " after the end (" + rec.ticks + ")");
                }
                previous = at;
                qx += zig(in);
                qy += zig(in);
                qz += zig(in);
                TrackSample s = new TrackSample();
                s.x = rec.originX + qx / POSITION_SCALE;
                s.y = rec.originY + qy / POSITION_SCALE;
                s.z = rec.originZ + qz / POSITION_SCALE;
                s.headYaw = in.readFloat();
                s.bodyYaw = in.readFloat();
                s.pitch = in.readFloat();
                s.walkPos = in.readFloat();
                s.walkSpeed = in.readFloat();
                s.attackAnim = in.readFloat();
                s.health = in.readFloat();
                s.absorption = in.readFloat();
                s.sharedFlags = in.readByte();
                s.livingFlags = in.readByte();
                s.pose = in.readByte();
                s.hurtTime = in.readByte();
                s.deathTime = in.readByte();
                s.anim = in.readByte();
                long use = zig(in);
                s.useTicks = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, use));
                track.frames.add(new ReplayRecording.Frame(at, s));
            }
            rec.tracks.add(track);
        }
        rec.victimTrack = trackIndex(victim, tracks, "victim");
        rec.killerTrack = trackIndex(killer, tracks, "killer");

        int events = count(in, 0, MAX_EVENTS, "events");
        long tick = 0;
        for (int e = 0; e < events; e++) {
            tick += zig(in);
            byte type = in.readByte();
            int a = trackIndex(varint(in) - 1, tracks, "event target");
            int b = trackIndex(varint(in) - 1, tracks, "event source");
            String note = in.readBoolean() ? string(in) : null;
            if (tick < 0 || tick >= rec.ticks || a < 0) {
                throw new ReplayFormatException("bad event at tick " + tick + " on track " + a);
            }
            rec.events.add(new ReplayRecording.Event((int) tick, type, a, b, note));
        }
        return rec;
    }

    // ---- primitives ----

    static long quantize(double offset) {
        double q = Math.rint(offset * POSITION_SCALE);
        return Double.isFinite(q) ? (long) q : 0L;
    }

    private static double finite(double v) throws ReplayFormatException {
        if (!Double.isFinite(v)) {
            throw new ReplayFormatException("non-finite origin");
        }
        return v;
    }

    private static int itemIndex(DataInput in, int items) throws IOException {
        int index = varint(in) - 1;
        if (index < -1 || index >= items) {
            throw new ReplayFormatException("item #" + index + " of " + items);
        }
        return index;
    }

    private static int trackIndex(int index, int tracks, String what) throws ReplayFormatException {
        if (index < -1 || index >= tracks) {
            throw new ReplayFormatException(what + " track #" + index + " of " + tracks);
        }
        return index;
    }

    private static int count(DataInput in, int min, int max, String what) throws IOException {
        int n = varint(in);
        if (n < min || n > max) {
            throw new ReplayFormatException("bad " + what + " count " + n);
        }
        return n;
    }

    static void varint(DataOutput out, int value) throws IOException {
        varlong(out, value & 0xFFFFFFFFL);
    }

    private static void varlong(DataOutput out, long value) throws IOException {
        long v = value;
        while ((v & ~0x7FL) != 0) {
            out.writeByte((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.writeByte((int) v);
    }

    static int varint(DataInput in) throws IOException {
        long v = varlong(in, 5);
        if (v > Integer.MAX_VALUE) {
            throw new ReplayFormatException("number out of range");
        }
        return (int) v;
    }

    private static long varlong(DataInput in, int maxBytes) throws IOException {
        long result = 0;
        for (int i = 0; i < maxBytes; i++) {
            int b = in.readUnsignedByte();
            result |= (long) (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new ReplayFormatException("number too long");
    }

    static void zig(DataOutput out, long value) throws IOException {
        varlong(out, (value << 1) ^ (value >> 63));
    }

    static long zig(DataInput in) throws IOException {
        long v = varlong(in, 10);
        return (v >>> 1) ^ -(v & 1);
    }

    static void string(DataOutput out, @Nullable String text) throws IOException {
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            bytes = Arrays.copyOf(bytes, 0);
        }
        varint(out, bytes.length);
        out.write(bytes);
    }

    static String string(DataInput in) throws IOException {
        int length = count(in, 0, MAX_STRING_BYTES, "string length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Stops decompression bombs: fails once more than {@code limit} bytes were read. */
    private static final class Limited extends FilterInputStream {
        private final long limit;
        private long read;

        Limited(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(long n) throws ReplayFormatException {
            read += n;
            if (read > limit) {
                throw new ReplayFormatException("body larger than " + limit + " B when decompressed");
            }
        }
    }
}
