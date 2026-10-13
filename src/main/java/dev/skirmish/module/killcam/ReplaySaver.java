package dev.skirmish.module.killcam;

import com.mojang.authlib.properties.Property;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.OwnDeath;
import dev.skirmish.debug.DebugLog;
import dev.skirmish.module.killcam.library.ReplayCapture;
import dev.skirmish.module.killcam.library.ReplayHeader;
import dev.skirmish.module.killcam.library.ReplayKind;
import dev.skirmish.module.killcam.library.ReplayRecording;
import dev.skirmish.module.killcam.library.ReplayStore;
import dev.skirmish.util.ServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Saves windows of the live buffer to the library: on my deaths (after the tail is recorded), on my kills (a
 * little after the kill so the fall is in) and on the «Сохранить момент» key. The window is copied on the client
 * thread (the buffer keeps changing); encoding, compression, writing and the cleanup run on the store's thread.
 */
final class ReplaySaver {
    private final KillCamModule module;
    private final Recorder recorder;
    private final ReplayStore store;
    private final List<Pending> pending = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private int saving;

    /** A save waiting for its tail: captured once the buffer reaches {@code due} (or freezes / is about to reset). */
    private static final class Pending {
        final ReplayKind kind;
        final long focusTick;
        long due;
        final UUID victim;
        final @Nullable Combatant killer;
        final Set<String> victims = new LinkedHashSet<>();
        final String server;
        final String dimension;
        final @Nullable RegistryAccess registries;

        Pending(ReplayKind kind, long focusTick, long due, UUID victim, @Nullable Combatant killer, String server, String dimension,
                @Nullable RegistryAccess registries) {
            this.kind = kind;
            this.focusTick = focusTick;
            this.due = due;
            this.victim = victim;
            this.killer = killer;
            this.server = server;
            this.dimension = dimension;
            this.registries = registries;
        }
    }

    ReplaySaver(KillCamModule module, Recorder recorder, Path directory) {
        this.module = module;
        this.recorder = recorder;
        this.store = ReplayStore.withOwnThread(directory, new ReplayStore.Log() {
            @Override
            public void info(String message) {
                module.log(message);
            }

            @Override
            public void warn(String message) {
                DebugLog.log(KillCamModule.ID, "replays: " + message);
            }

            @Override
            public void error(String message, Throwable error) {
                module.error("replays: " + message, error);
            }
        });
    }

    ReplayStore store() {
        return store;
    }

    /** Called on the client thread after every save or library change (the library screen refreshes). */
    void onChange(Runnable listener) {
        listeners.add(listener);
    }

    void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    void changed() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    boolean isSaving() {
        return saving > 0;
    }

    // ---- triggers (client thread) ----

    void onOwnDeath(OwnDeath death) {
        ReplayBuffer buffer = recorder.buffer();
        Minecraft mc = Minecraft.getInstance();
        if (!module.saveDeaths() || buffer == null || recorder.death() != death || mc.player == null) {
            return;
        }
        long focus = recorder.deathTick();
        pending.add(new Pending(ReplayKind.DEATH, focus, focus + module.tailTicks(), mc.player.getUUID(), death.killer(),
                ServerContext.serverDisplayName(), ServerContext.dimension(), registries()));
        module.log("replay save: death at tick %d queued, saved after the %d-tick tail", focus, module.tailTicks());
    }

    void onKill(Combatant victim) {
        ReplayBuffer buffer = recorder.buffer();
        Minecraft mc = Minecraft.getInstance();
        if (!module.saveKills() || buffer == null || recorder.isFrozen() || mc.player == null || !victim.player()) {
            return;
        }
        long focus = buffer.currentTick() + 1;
        long due = focus + module.tailTicks();
        for (Pending p : pending) {
            if (p.kind == ReplayKind.KILL) {
                // Another kill before the first one was saved: one replay with both.
                p.victims.add(victim.name());
                p.due = due;
                module.log("replay save: kill of %s joins the queued kill replay (%s)", victim.name(), p.victims);
                return;
            }
        }
        Pending p = new Pending(ReplayKind.KILL, focus, due, victim.uuid(), null, ServerContext.serverDisplayName(),
                ServerContext.dimension(), registries());
        p.victims.add(victim.name());
        pending.add(p);
        module.log("replay save: kill of %s at tick %d queued", victim.name(), focus);
    }

    /** «Сохранить момент»: the last N seconds, now. */
    void saveClip() {
        ReplayBuffer buffer = recorder.buffer();
        if (buffer == null || buffer.isEmpty() || buffer.currentTick() - buffer.oldestTick() < 1) {
            module.log("clip not saved: nothing recorded yet");
            module.notice("skirmish.killcam.saved.nothing", true);
            return;
        }
        long end = buffer.currentTick();
        Pending clip = new Pending(ReplayKind.CLIP, -1, end, new UUID(0, 0), null, ServerContext.serverDisplayName(),
                ServerContext.dimension(), registries());
        capture(buffer, clip, "key");
    }

    private static @Nullable RegistryAccess registries() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : null;
    }

    /** End of client tick, after the recorder: saves whatever is due. */
    void tick() {
        ReplayBuffer buffer = recorder.buffer();
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (buffer == null || buffer.isEmpty()) {
                it.remove();
                continue;
            }
            if (buffer.currentTick() >= p.due || recorder.isFrozen()) {
                it.remove();
                capture(buffer, p, recorder.isFrozen() && buffer.currentTick() < p.due ? "buffer frozen" : "tail recorded");
            }
        }
    }

    /** The recorder is about to drop its buffer (world change, disconnect, respawn): save what is queued now. */
    void flush(String reason) {
        ReplayBuffer buffer = recorder.buffer();
        List<Pending> queued = new ArrayList<>(pending);
        pending.clear();
        if (buffer == null || buffer.isEmpty()) {
            return;
        }
        for (Pending p : queued) {
            capture(buffer, p, "early: " + reason);
        }
    }

    // ---- capture ----

    private void capture(ReplayBuffer buffer, Pending p, String why) {
        long started = System.nanoTime();
        long to = Math.min(p.due, buffer.currentTick());
        long from = (p.kind == ReplayKind.CLIP ? to : p.focusTick) - module.preDeathTicks();
        Minecraft mc = Minecraft.getInstance();
        UUID me = mc.player != null ? mc.player.getUUID() : null;
        int self = ReplayBuffer.NO_TRACK;
        for (int t = 0; t < buffer.allocatedTracks(); t++) {
            if (buffer.isAssigned(t) && buffer.isSelf(t)) {
                self = t;
            }
        }
        if (self == ReplayBuffer.NO_TRACK && me != null) {
            self = buffer.findTrack(me);
        }
        int victim;
        int killer;
        String opponent;
        switch (p.kind) {
            case DEATH -> {
                victim = self;
                ReplaySession.Killer found = ReplaySession.resolveKiller(buffer, victim, p.killer, Math.max(from, buffer.oldestTick()), to);
                killer = found.track();
                opponent = killer != ReplayBuffer.NO_TRACK ? buffer.name(killer) : p.killer != null ? p.killer.name() : "";
            }
            case KILL -> {
                victim = buffer.findTrack(p.victim);
                killer = self;
                opponent = String.join(", ", p.victims);
            }
            default -> {
                killer = self;
                victim = lastOpponent(buffer, self, Math.max(from, buffer.oldestTick()), to);
                opponent = victim == ReplayBuffer.NO_TRACK ? "" : buffer.name(victim);
            }
        }
        ClientPacketListener connection = mc.getConnection();
        ReplayRecording rec = ReplayCapture.capture(buffer, from, to, p.focusTick, victim, killer,
                payload -> payload instanceof ItemStack stack ? ItemBlobs.encode(stack, p.registries, module) : null,
                (track, out) -> style(buffer, track, out, connection));
        if (rec == null || rec.tracks.isEmpty()) {
            module.log("replay save (%s) skipped: nothing recorded in ticks %d..%d", p.kind.id(), from, to);
            return;
        }
        ReplayHeader header = new ReplayHeader();
        header.createdMs = System.currentTimeMillis();
        header.kind = p.kind;
        header.server = p.server;
        header.dimension = p.dimension;
        header.ticks = rec.ticks;
        header.me = mc.player != null ? mc.player.getGameProfile().name() : mc.getUser().getName();
        header.opponent = ReplayHeader.clip(opponent, 64);
        header.players = rec.tracks.size();
        header.hits = rec.countEvents(ReplayBuffer.EVENT_HIT);
        header.crits = rec.countEvents(ReplayBuffer.EVENT_CRIT) + rec.countEvents(ReplayBuffer.EVENT_MAGIC_CRIT);
        header.totems = rec.countEvents(ReplayBuffer.EVENT_TOTEM);
        if (header.totems > 0) {
            header.tags.add("totem");
        }
        if (header.crits > 0) {
            header.tags.add("crit");
        }
        if (p.victims.size() > 1) {
            header.tags.add("multikill");
        }
        module.log("replay save (%s, %s): ticks %d..%d copied in %.2f ms, %d players, %d frames, %d items, %d events, victim %s, killer %s",
                p.kind.id(), why, from, to, (System.nanoTime() - started) / 1e6, rec.tracks.size(), rec.frameCount(), rec.items.size(),
                rec.events.size(), rec.name(rec.victimTrack), rec.name(rec.killerTrack));
        saving++;
        store.save(header, rec, new ReplayStore.Limits(module.maxReplays(), module.maxBytes())).whenComplete((saved, error) ->
                mc.execute(() -> {
                    saving--;
                    if (error != null) {
                        module.error("replay save failed (" + header + ")", error);
                        module.notice("skirmish.killcam.saved.failed", true);
                        return;
                    }
                    module.notice("skirmish.killcam.saved." + header.kind.id(), header.kind == ReplayKind.CLIP);
                    changed();
                }));
    }

    /** The player I last hit or was hit by in the window (clips). */
    private static int lastOpponent(ReplayBuffer buffer, int self, long from, long to) {
        if (self == ReplayBuffer.NO_TRACK) {
            return ReplayBuffer.NO_TRACK;
        }
        for (int i = buffer.eventCount() - 1; i >= 0; i--) {
            long tick = buffer.eventTick(i);
            if (buffer.eventType(i) != ReplayBuffer.EVENT_HIT || tick < from || tick > to) {
                continue;
            }
            int a = buffer.eventA(i);
            int b = buffer.eventB(i);
            if (a == self && b != ReplayBuffer.NO_TRACK && b != self) {
                return b;
            }
            if (b == self && a != self) {
                return a;
            }
        }
        return ReplayBuffer.NO_TRACK;
    }

    private static void style(ReplayBuffer buffer, int track, ReplayRecording.Track out, @Nullable ClientPacketListener connection) {
        if (buffer.meta(track) instanceof TrackMeta meta) {
            out.modelParts = meta.modelParts;
            out.leftHanded = meta.mainArm == HumanoidArm.LEFT;
        }
        UUID uuid = buffer.uuid(track);
        PlayerInfo info = connection == null || uuid == null ? null : connection.getPlayerInfo(uuid);
        if (info != null) {
            for (Property property : info.getProfile().properties().get(TrackMeta.TEXTURES)) {
                out.skinValue = property.value();
                out.skinSignature = property.signature() == null ? "" : property.signature();
                break;
            }
        }
    }
}
