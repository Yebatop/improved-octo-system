package dev.skirmish.module.killcam;

import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.EquipmentSnapshot;
import dev.skirmish.combat.OwnDeath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Writes the players around the local player into a {@link ReplayBuffer} at the end of every client tick and
 * turns combat events into buffer events. Stops ("freezes") a configurable tail after the own death so the
 * replay window stays put while the death screen is open; resumes with an empty buffer after respawn.
 */
final class Recorder {
    /** Longest pre-death window the {@code record_seconds} setting allows; the buffer is sized for it. */
    static final int MAX_PRE_DEATH_TICKS = 300;
    static final int MAX_TAIL_TICKS = 40;
    static final int CAPACITY = MAX_PRE_DEATH_TICKS + MAX_TAIL_TICKS;
    static final int MAX_TRACKS = 64;
    static final int EVENT_CAPACITY = 512;
    private static final int SLOTS = ReplayBuffer.EQUIPMENT_SLOTS;
    private static final int META_REFRESH_TICKS = 100;
    private static final long STATS_INTERVAL_MS = 10_000;
    private static final int EQUIPMENT_LOG_BUDGET = 40;

    private final KillCamModule module;
    private @Nullable ReplayBuffer buffer;
    private final TrackSample sample = new TrackSample();
    private final @Nullable ItemStack[] lastLive = new ItemStack[MAX_TRACKS * SLOTS];
    private final int[] seenGeneration = new int[MAX_TRACKS];
    private @Nullable ClientLevel level;

    private boolean frozen;
    private long deathTick = -1;
    private int tailTicks;
    private @Nullable OwnDeath death;
    private @Nullable LocalPlayer deadPlayer;

    private long statNanos;
    private long statMaxNanos;
    private int statTicks;
    private long statPlayers;
    private long lastStatsMs = System.currentTimeMillis();
    private int statEquipmentChanges;
    private int statEvents;
    private int statSkippedEvents;
    private int statTrackFull;
    private int equipmentLogBudget = EQUIPMENT_LOG_BUDGET;

    Recorder(KillCamModule module) {
        this.module = module;
    }

    @Nullable ReplayBuffer buffer() {
        return buffer;
    }

    boolean isFrozen() {
        return frozen;
    }

    long deathTick() {
        return deathTick;
    }

    @Nullable OwnDeath death() {
        return death;
    }

    /** Buffer holding a finished death of the current (dead) player. */
    boolean hasReplay(LocalPlayer player) {
        return buffer != null && !buffer.isEmpty() && deathTick >= 0 && deadPlayer == player
                && buffer.currentTick() - buffer.oldestTick() >= 1;
    }

    /** Drops everything; with {@code release} the arrays are freed too (module disabled). */
    void reset(String reason, boolean release) {
        if (buffer != null && !buffer.isEmpty()) {
            module.beforeBufferReset(reason);
        }
        if (buffer != null) {
            if (release) {
                buffer = null;
            } else {
                buffer.clear();
            }
        }
        java.util.Arrays.fill(lastLive, null);
        frozen = false;
        deathTick = -1;
        death = null;
        deadPlayer = null;
        module.log("recorder reset: %s%s", reason, release ? " (arrays released)" : "");
    }

    private ReplayBuffer ensureBuffer() {
        if (buffer == null) {
            buffer = new ReplayBuffer(CAPACITY, MAX_TRACKS, EVENT_CAPACITY);
            java.util.Arrays.fill(seenGeneration, 0);
            java.util.Arrays.fill(lastLive, null);
            module.log("buffer created: %d ticks x up to %d players, %d B per player, radius %.0f",
                    CAPACITY, MAX_TRACKS, ReplayBuffer.bytesPerTrack(CAPACITY), module.radius());
        }
        return buffer;
    }

    // ---- per tick ----

    void tick(Minecraft mc) {
        ClientLevel current = mc.level;
        LocalPlayer me = mc.player;
        if (current != level) {
            level = current;
            if (buffer != null && !buffer.isEmpty()) {
                reset(current == null ? "left the world" : "level changed to " + current.dimension().identifier(), false);
            }
        }
        if (current == null || me == null) {
            return;
        }
        if (frozen) {
            if (me != deadPlayer || !me.isDeadOrDying()) {
                if (!ReplaySession.isActive()) {
                    reset(me != deadPlayer ? "respawned (new player entity)" : "respawned (alive again)", false);
                }
            }
        } else {
            record(me, current.players());
        }
        maybeLogStats();
    }

    private void record(LocalPlayer me, List<AbstractClientPlayer> players) {
        long start = System.nanoTime();
        ReplayBuffer buf = ensureBuffer();
        long tick = buf.beginTick();
        double radius = module.radius();
        double radiusSq = radius * radius;
        int recorded = 0;
        for (int i = 0; i < players.size(); i++) {
            AbstractClientPlayer player = players.get(i);
            if (player instanceof ReplayPlayer || player.isRemoved()) {
                continue;
            }
            boolean self = player == me;
            if (!self && player.distanceToSqr(me) > radiusSq) {
                continue;
            }
            int track = buf.trackFor(player.getUUID(), player.getGameProfile().name(), self);
            if (track == ReplayBuffer.NO_TRACK) {
                statTrackFull++;
                continue;
            }
            if (seenGeneration[track] != buf.generation(track)) {
                onNewTrack(buf, track, player, self, Math.sqrt(player.distanceToSqr(me)));
            } else if ((tick + track) % META_REFRESH_TICKS == 0) {
                refreshMeta(buf, track, player);
            }
            fill(player);
            buf.write(track, sample);
            recordEquipment(buf, track, player, self);
            recorded++;
        }
        if (deathTick >= 0 && tick >= deathTick + tailTicks) {
            freeze("tail of " + tailTicks + " ticks after death recorded");
        }
        long elapsed = System.nanoTime() - start;
        statNanos += elapsed;
        statMaxNanos = Math.max(statMaxNanos, elapsed);
        statTicks++;
        statPlayers += recorded;
    }

    private void fill(AbstractClientPlayer p) {
        TrackSample s = sample;
        s.x = p.getX();
        s.y = p.getY();
        s.z = p.getZ();
        s.headYaw = p.yHeadRot;
        s.bodyYaw = p.yBodyRot;
        s.pitch = p.getXRot();
        s.walkPos = p.walkAnimation.position();
        s.walkSpeed = p.walkAnimation.speed();
        s.attackAnim = p.attackAnim;
        s.health = p.getHealth();
        s.absorption = p.getAbsorptionAmount();
        s.sharedFlags = ReplayPlayer.sharedFlags(p);
        s.livingFlags = ReplayPlayer.livingFlags(p);
        s.pose = (byte) p.getPose().id();
        s.hurtTime = (byte) Math.max(0, Math.min(127, p.hurtTime));
        s.deathTime = (byte) Math.max(0, Math.min(127, p.deathTime));
        int anim = 0;
        if (p.swinging) {
            anim |= TrackSample.ANIM_SWINGING;
        }
        if (p.swingingArm == InteractionHand.OFF_HAND) {
            anim |= TrackSample.ANIM_OFF_HAND;
        }
        if (p.onGround()) {
            anim |= TrackSample.ANIM_ON_GROUND;
        }
        s.anim = (byte) anim;
        s.useTicks = (short) Math.min(Short.MAX_VALUE, p.getTicksUsingItem());
    }

    private void onNewTrack(ReplayBuffer buf, int track, AbstractClientPlayer player, boolean self, double distance) {
        seenGeneration[track] = buf.generation(track);
        for (int slot = 0; slot < SLOTS; slot++) {
            lastLive[track * SLOTS + slot] = null;
        }
        TrackMeta meta = new TrackMeta(player.getUUID(), player.getGameProfile().name(), self);
        buf.setMeta(track, meta);
        refreshMeta(buf, track, player);
        module.log("track #%d -> %s%s (%s) at %.1f m, tick %d, %d/%d tracks in use",
                track, meta.name, self ? " (me)" : "", meta.uuid, distance, buf.currentTick(), buf.assignedTracks(), MAX_TRACKS);
    }

    private static void refreshMeta(ReplayBuffer buf, int track, AbstractClientPlayer player) {
        if (buf.meta(track) instanceof TrackMeta meta) {
            meta.skin = player.getSkin();
            meta.modelParts = ReplayPlayer.modelParts(player);
            meta.mainArm = player.getMainArm();
        }
    }

    private void recordEquipment(ReplayBuffer buf, int track, AbstractClientPlayer player, boolean self) {
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack live = player.getItemBySlot(EquipmentSnapshot.SLOTS.get(slot));
            int key = track * SLOTS + slot;
            // Remote stacks are replaced by equipment packets; the local player's stacks also change in place.
            if (live == lastLive[key] && !self) {
                continue;
            }
            lastLive[key] = live;
            Object stored = buf.latestEquipment(track, slot);
            ItemStack previous = stored instanceof ItemStack stack ? stack : ItemStack.EMPTY;
            if (sameLook(previous, live)) {
                continue;
            }
            ItemStack copy = live.isEmpty() ? ItemStack.EMPTY : live.copy();
            buf.setEquipment(track, slot, copy);
            statEquipmentChanges++;
            if (equipmentLogBudget > 0) {
                equipmentLogBudget--;
                module.log("equipment %s %s: %s -> %s (tick %d, %d changes stored for this player)", buf.name(track),
                        EquipmentSnapshot.SLOTS.get(slot).getName(), itemId(previous), itemId(copy), buf.currentTick(),
                        buf.equipmentChanges(track));
            }
        }
    }

    /** Equal as far as the player model shows it: same item and same enchantment glint (durability is ignored). */
    private static boolean sameLook(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() == b.isEmpty();
        }
        return ItemStack.isSameItem(a, b) && a.hasFoil() == b.hasFoil();
    }

    static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private void freeze(String reason) {
        if (frozen || buffer == null) {
            return;
        }
        frozen = true;
        ReplayBuffer buf = buffer;
        long start = Math.max(buf.oldestTick(), deathTick - module.preDeathTicks());
        module.log("buffer frozen (%s) at tick %d: window %d..%d = %.2f s (%.2f s before death, %.2f s after), %d players, %d events, %d equipment copies",
                reason, buf.currentTick(), start, buf.currentTick(), (buf.currentTick() - start + 1) / 20.0,
                (deathTick - start) / 20.0, (buf.currentTick() - deathTick + 1) / 20.0, buf.assignedTracks(), buf.eventCount(),
                buf.equipmentPayloads());
    }

    /** Called when a replay starts before the tail is complete. */
    void freezeForReplay() {
        if (deathTick >= 0) {
            freeze("replay started");
        }
    }

    // ---- combat events (client thread, between ticks) ----

    void onOwnDeath(OwnDeath ownDeath, LocalPlayer player) {
        ReplayBuffer buf = ensureBuffer();
        if (frozen) {
            module.log("own death ignored: buffer already frozen for an earlier death");
            return;
        }
        death = ownDeath;
        deadPlayer = player;
        deathTick = buf.currentTick() + 1;
        tailTicks = module.tailTicks();
        int self = buf.findTrack(player.getUUID());
        if (self != ReplayBuffer.NO_TRACK) {
            buf.addEvent(ReplayBuffer.EVENT_DEATH, self, killerTrack(ownDeath), null);
        }
        Combatant killer = ownDeath.killer();
        module.log("own death at tick %d: killer=%s (track %s), message=\"%s\", %.2f s recorded, recording %d more ticks",
                deathTick, killer == null ? "none" : killer.name(), killer == null ? "-" : trackText(buf.findTrack(killer.uuid())),
                ownDeath.message() == null ? "" : ownDeath.message().getString(),
                (buf.currentTick() - buf.oldestTick() + 1) / 20.0, tailTicks);
    }

    private int killerTrack(OwnDeath ownDeath) {
        Combatant killer = ownDeath.killer();
        return killer == null || buffer == null ? ReplayBuffer.NO_TRACK : buffer.findTrack(killer.uuid());
    }

    void onEvent(byte type, UUID target, @Nullable UUID other, @Nullable String note, String what) {
        if (frozen || buffer == null) {
            return;
        }
        int a = buffer.findTrack(target);
        if (a == ReplayBuffer.NO_TRACK) {
            statSkippedEvents++;
            return;
        }
        int b = other == null ? ReplayBuffer.NO_TRACK : buffer.findTrack(other);
        buffer.addEvent(type, a, b, note);
        statEvents++;
        module.log("event %s stamped tick %d: %s", eventName(type), buffer.currentTick() + 1, what);
    }

    static String eventName(byte type) {
        return switch (type) {
            case ReplayBuffer.EVENT_HIT -> "HIT";
            case ReplayBuffer.EVENT_CRIT -> "CRIT";
            case ReplayBuffer.EVENT_MAGIC_CRIT -> "MAGIC_CRIT";
            case ReplayBuffer.EVENT_TOTEM -> "TOTEM";
            case ReplayBuffer.EVENT_DEATH -> "DEATH";
            default -> "?" + type;
        };
    }

    private static String trackText(int track) {
        return track == ReplayBuffer.NO_TRACK ? "not recorded" : "#" + track;
    }

    private void maybeLogStats() {
        long now = System.currentTimeMillis();
        if (now - lastStatsMs < STATS_INTERVAL_MS) {
            return;
        }
        lastStatsMs = now;
        if (module.isDebug()) {
            ReplayBuffer buf = buffer;
            long memory = buf == null ? 0 : buf.memoryBytes();
            if (statTicks == 0) {
                module.log("stats 10s: not recording (%s), buffer %.1f KiB", frozen ? "frozen after death" : "no world", memory / 1024.0);
            } else {
                module.log(String.format(Locale.ROOT,
                        "stats 10s: %d ticks, record avg %.1f us max %.1f us, avg %.1f players/tick, buffer %.1f KiB (%d tracks allocated, %d in use), "
                                + "%d equipment copies stored, %d equipment changes, %d events (+%d skipped: not recorded players), %d track-limit drops, %d equipment overflows",
                        statTicks, statNanos / 1000.0 / statTicks, statMaxNanos / 1000.0, (double) statPlayers / statTicks, memory / 1024.0,
                        buf == null ? 0 : buf.allocatedTracks(), buf == null ? 0 : buf.assignedTracks(),
                        buf == null ? 0 : buf.equipmentPayloads(), statEquipmentChanges, statEvents, statSkippedEvents, statTrackFull,
                        buf == null ? 0 : buf.equipmentOverflows()));
            }
        }
        statNanos = 0;
        statMaxNanos = 0;
        statTicks = 0;
        statPlayers = 0;
        statEquipmentChanges = 0;
        statEvents = 0;
        statSkippedEvents = 0;
        statTrackFull = 0;
        equipmentLogBudget = EQUIPMENT_LOG_BUDGET;
    }
}
