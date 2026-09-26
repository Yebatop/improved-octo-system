package dev.skirmish.module.killcam.library;

import dev.skirmish.module.killcam.ReplayBuffer;
import dev.skirmish.module.killcam.TrackSample;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayCodecTest {
    private static final double POSITION_EPS = 0.5 / ReplayCodec.POSITION_SCALE + 1e-9;

    static ReplayHeader header() {
        ReplayHeader h = new ReplayHeader();
        h.createdMs = 1_790_000_000_000L;
        h.kind = ReplayKind.KILL;
        h.server = "mc.holyworld.ru";
        h.dimension = "minecraft:overworld";
        h.ticks = 12;
        h.me = "Steve";
        h.opponent = "Notch";
        h.title = "Лучший килл";
        h.favourite = true;
        h.tags = List.of("totem", "crit");
        h.players = 2;
        h.hits = 3;
        h.crits = 1;
        h.totems = 1;
        return h;
    }

    static ReplayHeader header(int ticks) {
        ReplayHeader h = header();
        h.ticks = ticks;
        return h;
    }

    static TrackSample sample(double x, double y, double z, float yaw) {
        TrackSample s = new TrackSample();
        s.x = x;
        s.y = y;
        s.z = z;
        s.headYaw = yaw;
        s.bodyYaw = yaw - 12.5F;
        s.pitch = -33.25F;
        s.walkPos = 7.125F;
        s.walkSpeed = 0.75F;
        s.attackAnim = 0.4F;
        s.health = 17.5F;
        s.absorption = 4F;
        s.sharedFlags = (byte) 0x88;
        s.livingFlags = 3;
        s.pose = 5;
        s.hurtTime = 9;
        s.deathTime = 0;
        s.anim = TrackSample.ANIM_SWINGING | TrackSample.ANIM_ON_GROUND;
        s.useTicks = 31;
        return s;
    }

    static ReplayRecording recording() {
        ReplayRecording rec = new ReplayRecording();
        rec.ticks = 12;
        rec.focusTick = 10;
        rec.victimTrack = 1;
        rec.killerTrack = 0;
        rec.originX = 12_000;
        rec.originY = 64;
        rec.originZ = -8_000;
        rec.items.add(new ReplayRecording.Item("minecraft:netherite_sword", 1, true, "sword-nbt".getBytes(StandardCharsets.UTF_8)));
        rec.items.add(new ReplayRecording.Item("minecraft:totem_of_undying", 1, false, new byte[0]));

        ReplayRecording.Track me = new ReplayRecording.Track();
        me.uuid = new UUID(1, 2);
        me.name = "Steve";
        me.self = true;
        me.modelParts = 0x3F;
        me.skinValue = "eyJ0ZXh0dXJlcyI6e319";
        me.skinSignature = "c2ln";
        me.baseItems[4] = 0;
        me.changes.add(new ReplayRecording.EquipmentChange(3, 5, 1));
        me.changes.add(new ReplayRecording.EquipmentChange(9, 5, -1));
        for (int t = 0; t < 12; t++) {
            if (t == 4 || t == 5) {
                continue;
            }
            me.frames.add(new ReplayRecording.Frame(t, sample(12_000.123456 + t * 0.21, 64.0 + t * 0.05, -8_000.5 - t, 170F + t * 5F)));
        }
        ReplayRecording.Track notch = new ReplayRecording.Track();
        notch.uuid = new UUID(3, 4);
        notch.name = "Notch";
        notch.leftHanded = true;
        for (int t = 2; t < 12; t++) {
            notch.frames.add(new ReplayRecording.Frame(t, sample(12_003.9 - t, 63.5, -7_998.0 + t * 0.33, -90F)));
        }
        rec.tracks.add(me);
        rec.tracks.add(notch);
        rec.events.add(new ReplayRecording.Event(2, ReplayBuffer.EVENT_HIT, 1, 0, "minecraft:player_attack"));
        rec.events.add(new ReplayRecording.Event(6, ReplayBuffer.EVENT_CRIT, 1, -1, null));
        rec.events.add(new ReplayRecording.Event(7, ReplayBuffer.EVENT_TOTEM, 1, -1, null));
        rec.events.add(new ReplayRecording.Event(10, ReplayBuffer.EVENT_DEATH, 1, 0, null));
        return rec;
    }

    static void assertSampleEquals(TrackSample expected, TrackSample actual) {
        assertEquals(expected.x, actual.x, POSITION_EPS);
        assertEquals(expected.y, actual.y, POSITION_EPS);
        assertEquals(expected.z, actual.z, POSITION_EPS);
        assertEquals(expected.headYaw, actual.headYaw);
        assertEquals(expected.bodyYaw, actual.bodyYaw);
        assertEquals(expected.pitch, actual.pitch);
        assertEquals(expected.walkPos, actual.walkPos);
        assertEquals(expected.walkSpeed, actual.walkSpeed);
        assertEquals(expected.attackAnim, actual.attackAnim);
        assertEquals(expected.health, actual.health);
        assertEquals(expected.absorption, actual.absorption);
        assertEquals(expected.sharedFlags, actual.sharedFlags);
        assertEquals(expected.livingFlags, actual.livingFlags);
        assertEquals(expected.pose, actual.pose);
        assertEquals(expected.hurtTime, actual.hurtTime);
        assertEquals(expected.deathTime, actual.deathTime);
        assertEquals(expected.anim, actual.anim);
        assertEquals(expected.useTicks, actual.useTicks);
    }

    static void assertRecordingEquals(ReplayRecording expected, ReplayRecording actual) {
        assertEquals(expected.ticks, actual.ticks);
        assertEquals(expected.focusTick, actual.focusTick);
        assertEquals(expected.victimTrack, actual.victimTrack);
        assertEquals(expected.killerTrack, actual.killerTrack);
        assertEquals(expected.originX, actual.originX);
        assertEquals(expected.originY, actual.originY);
        assertEquals(expected.originZ, actual.originZ);
        assertEquals(expected.items, actual.items);
        assertEquals(expected.events, actual.events);
        assertEquals(expected.tracks.size(), actual.tracks.size());
        for (int i = 0; i < expected.tracks.size(); i++) {
            ReplayRecording.Track e = expected.tracks.get(i);
            ReplayRecording.Track a = actual.tracks.get(i);
            assertEquals(e.uuid, a.uuid);
            assertEquals(e.name, a.name);
            assertEquals(e.self, a.self);
            assertEquals(e.leftHanded, a.leftHanded);
            assertEquals(e.modelParts, a.modelParts);
            assertEquals(e.skinValue, a.skinValue);
            assertEquals(e.skinSignature, a.skinSignature);
            assertArrayEquals(e.baseItems, a.baseItems);
            assertEquals(e.changes, a.changes);
            assertEquals(e.frames.size(), a.frames.size());
            for (int f = 0; f < e.frames.size(); f++) {
                assertEquals(e.frames.get(f).tick(), a.frames.get(f).tick());
                assertSampleEquals(e.frames.get(f).sample(), a.frames.get(f).sample());
            }
        }
    }

    @Test
    void roundTripKeepsHeaderFramesEquipmentAndEvents() throws IOException {
        ReplayRecording rec = recording();
        byte[] file = ReplayCodec.encode(header(), rec);
        ReplayCodec.Decoded decoded = ReplayCodec.decode(file);
        assertEquals(header().toJson(), decoded.header().toJson());
        assertRecordingEquals(rec, decoded.recording());
        assertNull(decoded.recording().events.get(1).note());
    }

    @Test
    void headerIsReadableWithoutTheBody() throws IOException {
        byte[] file = ReplayCodec.encode(header(), recording());
        ReplayHeader h = ReplayCodec.readHeader(new ByteArrayInputStream(file));
        assertEquals("Notch", h.opponent);
        assertEquals("Лучший килл", h.title);
        assertEquals(ReplayKind.KILL, h.kind);
        assertTrue(h.favourite);
        assertEquals(List.of("totem", "crit"), h.tags);
        assertEquals(0.6, h.seconds(), 1e-9);
    }

    @Test
    void positionsFarFromTheOriginKeepTheirResolution() throws IOException {
        ReplayRecording rec = recording();
        rec.tracks.get(1).frames.getFirst().sample().x = 12_000 + 250_000.000_1;
        ReplayRecording back = ReplayCodec.decode(ReplayCodec.encode(header(), rec)).recording();
        assertEquals(262_000.000_1, back.tracks.get(1).frames.getFirst().sample().x, POSITION_EPS);
    }

    @Test
    void withHeaderChangesOnlyTheHeader() throws IOException {
        byte[] file = ReplayCodec.encode(header(), recording());
        ReplayHeader renamed = header();
        renamed.title = "Камбэк";
        renamed.favourite = false;
        byte[] updated = ReplayCodec.withHeader(file, renamed);
        ReplayCodec.Decoded decoded = ReplayCodec.decode(updated);
        assertEquals("Камбэк", decoded.header().title);
        assertEquals(false, decoded.header().favourite);
        assertRecordingEquals(recording(), decoded.recording());
        byte[] tailBefore = Arrays.copyOfRange(file, file.length - 64, file.length);
        byte[] tailAfter = Arrays.copyOfRange(updated, updated.length - 64, updated.length);
        assertArrayEquals(tailBefore, tailAfter, "compressed frames are copied byte for byte");
    }

    @Test
    void newerFormatVersionIsRefused() throws IOException {
        byte[] file = ReplayCodec.encode(header(), recording());
        file[5] = (byte) (ReplayCodec.VERSION + 1);
        ReplayFormatException e = assertThrows(ReplayFormatException.class, () -> ReplayCodec.decode(file));
        assertEquals(ReplayFormatException.Problem.NEWER_VERSION, e.problem());
        assertThrows(ReplayFormatException.class, () -> ReplayCodec.readHeader(new ByteArrayInputStream(file)));
    }

    @Test
    void formatVersionsBelowTheMinimumAreRefused() throws IOException {
        byte[] file = ReplayCodec.encode(header(), recording());
        file[4] = 0;
        file[5] = (byte) (ReplayCodec.MIN_VERSION - 1);
        ReplayFormatException e = assertThrows(ReplayFormatException.class, () -> ReplayCodec.decode(file));
        assertEquals(ReplayFormatException.Problem.OLD_VERSION, e.problem());
    }

    @Test
    void currentVersionIsWrittenAfterTheMagic() throws IOException {
        byte[] file = ReplayCodec.encode(header(), recording());
        assertArrayEquals("SKRP".getBytes(StandardCharsets.US_ASCII), Arrays.copyOf(file, 4));
        assertEquals(ReplayCodec.VERSION, ((file[4] & 0xFF) << 8) | (file[5] & 0xFF));
    }

    @Test
    void foreignTruncatedAndDamagedFilesAreRejected() throws IOException {
        byte[] file = ReplayCodec.encode(header(), recording());

        ReplayFormatException foreign = assertThrows(ReplayFormatException.class,
                () -> ReplayCodec.decode("PK\u0003\u0004 not a replay".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals(ReplayFormatException.Problem.NOT_A_REPLAY, foreign.problem());

        for (int length : new int[]{0, 3, 6, 20, file.length / 2, file.length - 1}) {
            byte[] cut = Arrays.copyOf(file, length);
            ReplayFormatException e = assertThrows(ReplayFormatException.class, () -> ReplayCodec.decode(cut), "cut at " + length);
            assertEquals(ReplayFormatException.Problem.CORRUPT, e.problem());
        }

        byte[] damaged = file.clone();
        damaged[damaged.length - 10] ^= 0x55;
        ReplayFormatException crc = assertThrows(ReplayFormatException.class, () -> ReplayCodec.decode(damaged));
        assertTrue(crc.getMessage().contains("checksum"), crc.getMessage());

        byte[] badJson = file.clone();
        badJson[10] = '[';
        assertThrows(ReplayFormatException.class, () -> ReplayCodec.decode(badJson));
    }

    @Test
    void badIndexesInTheBodyAreRejected() throws IOException {
        ReplayRecording rec = recording();
        rec.tracks.get(0).baseItems[0] = 7;
        byte[] file = ReplayCodec.encode(header(), rec);
        assertThrows(ReplayFormatException.class, () -> ReplayCodec.decode(file));

        ReplayRecording twice = recording();
        twice.tracks.get(1).uuid = twice.tracks.get(0).uuid;
        byte[] duplicate = ReplayCodec.encode(header(), twice);
        assertThrows(ReplayFormatException.class, () -> ReplayCodec.decode(duplicate));

        ReplayHeader wrongLength = header();
        wrongLength.ticks = 99;
        byte[] mismatch = ReplayCodec.encode(wrongLength, recording());
        assertThrows(ReplayFormatException.class, () -> ReplayCodec.decode(mismatch));
    }

    @Test
    void headerJsonToleratesUnknownAndMissingOptionalKeys() throws ReplayFormatException {
        com.google.gson.JsonObject json = header().toJson();
        json.addProperty("future_field", 42);
        json.remove("tags");
        json.remove("title");
        ReplayHeader h = ReplayHeader.fromJson(json);
        assertEquals("", h.title);
        assertTrue(h.tags.isEmpty());
        json.addProperty("kind", "victory_lap");
        assertThrows(ReplayFormatException.class, () -> ReplayHeader.fromJson(json));
    }

    @Test
    void searchMatchesNamesTitleServerAndTags() {
        ReplayHeader h = header();
        assertTrue(h.matches(""));
        assertTrue(h.matches("notch"));
        assertTrue(h.matches("  HOLYWORLD "));
        assertTrue(h.matches("лучший"));
        assertTrue(h.matches("totem"));
        assertTrue(!h.matches("herobrine"));
    }
}
