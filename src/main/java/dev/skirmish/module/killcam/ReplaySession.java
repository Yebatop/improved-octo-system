package dev.skirmish.module.killcam;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.OwnDeath;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One running replay: the fake players in the client level, the replay clock and the camera. The mixins ask
 * {@link #current()} every frame; while it is non-null the camera, FOV, hurt/death tilt, hand, HUD and the
 * rendering of real players are overridden. Everything is undone in {@link #stop}.
 */
final class ReplaySession {
    static final int SCREEN_DEATH = 0;
    static final int SCREEN_CLOSE = 1;
    static final int SCREEN_KEEP = 2;
    static final double[] SPEEDS = {0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
    private static final int FAKE_ID_BASE = -1_000_000;
    private static final float MIN_DISTANCE = 1.5F;
    private static final float MAX_DISTANCE = 16.0F;
    private static @Nullable ReplaySession current;

    final KillCamModule module;
    final ReplayBuffer buffer;
    final ClientLevel level;
    final LocalPlayer deadPlayer;
    final @Nullable Component deathMessage;
    final boolean hardcore;
    final long startTick;
    final long endTick;
    final long deathTick;
    final int victimTrack;
    final int killerTrack;
    final String killerName;
    final String killerSource;
    private final List<ReplayPlayer> fakes = new ArrayList<>();
    private final @Nullable ReplayPlayer[] fakeByTrack;
    private final TrackSample sample = new TrackSample();
    private final TrackSample hudSample = new TrackSample();
    private final java.util.Set<Integer> hudMissLogged = new java.util.HashSet<>();

    private double time;
    private boolean playing = true;
    private double speed;
    private CameraMode mode;
    private long lastFrameNanos;
    private boolean seeked = true;
    private @Nullable ReplayPlayer hiddenFake;
    private String cameraNote = "";

    double camX;
    double camY;
    double camZ;
    float camYaw;
    float camPitch;
    private boolean cameraPlaced;
    private float orbitYaw;
    private float orbitPitch = 20.0F;
    private float orbitDistance = 4.0F;
    private boolean orbitInitialized;
    private double freeX;
    private double freeY;
    private double freeZ;
    private float freeYaw;
    private float freePitch;
    private float freeSpeed = 6.0F;

    private final long startedMs = System.currentTimeMillis();
    private int frames;
    private long frameNanos;
    private long frameMaxNanos;
    private int eventsFired;

    private ReplaySession(KillCamModule module, ReplayBuffer buffer, ClientLevel level, LocalPlayer deadPlayer, @Nullable OwnDeath death,
                          long startTick, long endTick, long deathTick, int victimTrack, int killerTrack, String killerSource) {
        this.module = module;
        this.buffer = buffer;
        this.level = level;
        this.deadPlayer = deadPlayer;
        this.deathMessage = death == null ? null : death.message();
        this.hardcore = level.getLevelData().isHardcore();
        this.startTick = startTick;
        this.endTick = endTick;
        this.deathTick = deathTick;
        this.victimTrack = victimTrack;
        this.killerTrack = killerTrack;
        this.killerName = killerTrack == ReplayBuffer.NO_TRACK ? "" : buffer.name(killerTrack);
        this.killerSource = killerSource;
        this.fakeByTrack = new ReplayPlayer[buffer.allocatedTracks()];
        this.time = startTick;
        this.speed = module.defaultSpeed();
        this.mode = module.defaultCamera();
    }

    static @Nullable ReplaySession current() {
        return current;
    }

    static boolean isActive() {
        return current != null;
    }

    /** Starts a replay of the frozen buffer, or returns null (reason in debug.log). */
    static @Nullable ReplaySession start(KillCamModule module, Recorder recorder, String trigger) {
        Minecraft mc = Minecraft.getInstance();
        if (current != null) {
            module.log("replay not started (%s): already running", trigger);
            return null;
        }
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            module.log("replay not started (%s): no world", trigger);
            return null;
        }
        if (!player.isDeadOrDying()) {
            module.log("replay not started (%s): player is alive", trigger);
            return null;
        }
        if (!recorder.hasReplay(player)) {
            module.log("replay not started (%s): nothing recorded for this death (buffer empty or no death packet seen)", trigger);
            return null;
        }
        recorder.freezeForReplay();
        ReplayBuffer buffer = recorder.buffer();
        if (buffer == null) {
            return null;
        }
        long end = buffer.currentTick();
        long death = recorder.deathTick();
        long start = Math.max(buffer.oldestTick(), death - module.preDeathTicks());
        int victim = buffer.findTrack(player.getUUID());
        OwnDeath ownDeath = recorder.death();

        int killer = ReplayBuffer.NO_TRACK;
        String source;
        Combatant killerCombatant = ownDeath == null ? null : ownDeath.killer();
        if (killerCombatant != null && !killerCombatant.self() && buffer.findTrack(killerCombatant.uuid()) != ReplayBuffer.NO_TRACK) {
            killer = buffer.findTrack(killerCombatant.uuid());
            source = "OwnDeath.killer " + killerCombatant.name();
        } else {
            for (int i = buffer.eventCount() - 1; i >= 0; i--) {
                if (buffer.eventType(i) == ReplayBuffer.EVENT_HIT && buffer.eventA(i) == victim && buffer.eventB(i) != ReplayBuffer.NO_TRACK
                        && buffer.eventB(i) != victim && buffer.eventTick(i) >= start && buffer.eventTick(i) <= end) {
                    killer = buffer.eventB(i);
                    break;
                }
            }
            source = killer != ReplayBuffer.NO_TRACK ? "last recorded hit on me"
                    : killerCombatant != null ? "none: killer " + killerCombatant.name() + " was not within the recording radius"
                    : "none: no attacker known";
        }

        ReplaySession session = new ReplaySession(module, buffer, level, player, ownDeath, start, end, death, victim, killer, source);
        session.spawnFakes();
        current = session;
        module.log("replay start (%s): window %d..%d = %.2f s, death at %.2f s, victim track %s, killer %s (%s), camera %s, speed %.2fx, %d fakes",
                trigger, start, end, (end - start) / 20.0, (death - start) / 20.0, victim, session.killerName.isEmpty() ? "-" : session.killerName,
                source, session.mode, session.speed, session.fakes.size());
        mc.setScreen(new ReplayScreen(session));
        return session;
    }

    private void spawnFakes() {
        for (int track = 0; track < buffer.allocatedTracks(); track++) {
            if (!buffer.isAssigned(track) || !(buffer.meta(track) instanceof TrackMeta meta)) {
                continue;
            }
            long first = buffer.firstPresent(track, startTick, endTick);
            if (first < 0) {
                continue;
            }
            ReplayPlayer fake = new ReplayPlayer(level, FAKE_ID_BASE - track, track, meta);
            buffer.read(track, first, sample);
            applyEquipment(fake, first);
            fake.apply(sample, 1.0F);
            level.addEntity(fake);
            fakes.add(fake);
            fakeByTrack[track] = fake;
            module.log("fake for %s: entity id %d, uuid %s (original %s), skin from %s, first seen at %.2f s, %d equipment changes",
                    meta.name, fake.getId(), fake.getUUID(), meta.uuid, fake.skinSource, (first - startTick) / 20.0, buffer.equipmentChanges(track));
        }
    }

    private void applyEquipment(ReplayPlayer fake, long tick) {
        for (int slot = 0; slot < ReplayBuffer.EQUIPMENT_SLOTS; slot++) {
            fake.applyEquipment(slot, buffer.equipmentAt(fake.track, slot, tick));
        }
    }

    // ---- per frame (Camera.setup) ----

    void onFrame(float partialTick) {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0 ? 0 : Math.min(0.25, (now - lastFrameNanos) / 1.0e9);
        lastFrameNanos = now;
        double before = time;
        if (playing) {
            time += dt * 20.0 * speed;
            if (time >= endTick) {
                if (module.loop()) {
                    time = startTick;
                    seeked = true;
                    module.log("replay looped");
                } else {
                    time = endTick;
                    playing = false;
                    module.log("replay reached the end (%.2f s), paused", duration());
                }
            }
        }
        if (!seeked && time > before) {
            fireEvents(before, time);
        }
        seeked = false;

        long tick = (long) Math.floor(time);
        for (ReplayPlayer fake : fakes) {
            fake.present = buffer.sample(fake.track, time, sample);
            if (fake.present) {
                applyEquipment(fake, tick);
                fake.apply(sample, partialTick);
            }
        }
        updateCamera(dt);
        long elapsed = System.nanoTime() - now;
        frames++;
        frameNanos += elapsed;
        frameMaxNanos = Math.max(frameMaxNanos, elapsed);
    }

    private void fireEvents(double from, double to) {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < buffer.eventCount(); i++) {
            long tick = buffer.eventTick(i);
            if (tick <= from || tick > to || tick < startTick) {
                continue;
            }
            ReplayPlayer target = fake(buffer.eventA(i));
            if (target == null) {
                continue;
            }
            byte type = buffer.eventType(i);
            boolean particles = module.particles();
            switch (type) {
                case ReplayBuffer.EVENT_HIT -> sound(target, SoundEvents.PLAYER_HURT);
                case ReplayBuffer.EVENT_CRIT -> {
                    if (particles) {
                        mc.particleEngine.createTrackingEmitter(target, ParticleTypes.CRIT);
                    }
                    sound(target, SoundEvents.PLAYER_ATTACK_CRIT);
                }
                case ReplayBuffer.EVENT_MAGIC_CRIT -> {
                    if (particles) {
                        mc.particleEngine.createTrackingEmitter(target, ParticleTypes.ENCHANTED_HIT);
                    }
                }
                case ReplayBuffer.EVENT_TOTEM -> {
                    if (particles) {
                        mc.particleEngine.createTrackingEmitter(target, ParticleTypes.TOTEM_OF_UNDYING, 30);
                    }
                    sound(target, SoundEvents.TOTEM_USE);
                }
                default -> {
                }
            }
            eventsFired++;
            module.log("replay %.2f s: %s on %s%s", (tick - startTick) / 20.0, Recorder.eventName(type), buffer.name(buffer.eventA(i)),
                    particles && type != ReplayBuffer.EVENT_HIT && type != ReplayBuffer.EVENT_DEATH ? " (particles)" : "");
        }
    }

    private void sound(Entity at, SoundEvent sound) {
        if (module.sounds()) {
            level.playLocalSound(at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, 1.0F, 1.0F, false);
        }
    }

    private void updateCamera(double dt) {
        hiddenFake = null;
        ReplayPlayer killer = present(fake(killerTrack));
        ReplayPlayer victim = present(fake(victimTrack));
        switch (mode) {
            case KILLER -> {
                if (killer != null) {
                    Vec3 eye = killer.getEyePosition();
                    place(eye.x, eye.y, eye.z, killer.yHeadRot, killer.getXRot());
                    hiddenFake = killer;
                    cameraNote = "";
                } else {
                    cameraNote = killerTrack == ReplayBuffer.NO_TRACK ? "no_killer" : "killer_away";
                    orbit(victim);
                }
            }
            case ORBIT -> {
                cameraNote = killer == null ? (killerTrack == ReplayBuffer.NO_TRACK ? "no_killer" : "killer_away") : "";
                orbit(killer != null ? killer : victim);
            }
            case FREE -> {
                cameraNote = "";
                moveFree(dt);
                place(freeX, freeY, freeZ, freeYaw, freePitch);
            }
        }
        if (!cameraPlaced) {
            Vec3 eye = deadPlayer.getEyePosition();
            place(eye.x, eye.y, eye.z, deadPlayer.getYHeadRot(), 0.0F);
        }
    }

    private void orbit(@Nullable ReplayPlayer target) {
        if (target == null) {
            return;
        }
        if (!orbitInitialized) {
            orbitInitialized = true;
            orbitYaw = target.yHeadRot;
        }
        Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        Vec3 forward = Vec3.directionFromRotation(orbitPitch, orbitYaw);
        Vec3 desired = center.subtract(forward.scale(orbitDistance));
        HitResult hit = level.clip(new ClipContext(center, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 pos = desired;
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 back = hit.getLocation().subtract(center);
            double length = back.length();
            pos = length > 0.3 ? center.add(back.scale((length - 0.2) / length)) : center;
        }
        place(pos.x, pos.y, pos.z, orbitYaw, orbitPitch);
    }

    private void moveFree(double dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ReplayScreen) || dt <= 0) {
            return;
        }
        double forward = axis(mc, mc.options.keyUp, mc.options.keyDown);
        double strafe = axis(mc, mc.options.keyLeft, mc.options.keyRight);
        double vertical = axis(mc, mc.options.keyJump, mc.options.keyShift);
        if (forward == 0 && strafe == 0 && vertical == 0) {
            return;
        }
        double step = freeSpeed * dt * (down(mc, mc.options.keySprint) ? 3.0 : 1.0);
        double yaw = Math.toRadians(freeYaw);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        freeX += (-sin * forward + cos * strafe) * step;
        freeZ += (cos * forward + sin * strafe) * step;
        freeY += vertical * step;
    }

    private static double axis(Minecraft mc, KeyMapping positive, KeyMapping negative) {
        return (down(mc, positive) ? 1 : 0) - (down(mc, negative) ? 1 : 0);
    }

    private static boolean down(Minecraft mc, KeyMapping mapping) {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0 && InputConstants.isKeyDown(mc.getWindow(), key.getValue());
    }

    private void place(double x, double y, double z, float yaw, float pitch) {
        camX = x;
        camY = y;
        camZ = z;
        camYaw = yaw;
        camPitch = pitch;
        cameraPlaced = true;
    }

    // ---- queries for mixins and the screen ----

    /** Real players (and optionally drops/projectiles) are hidden; fakes only while present and not the POV. */
    boolean hides(Entity entity) {
        if (entity instanceof ReplayPlayer fake) {
            return !fake.present || fake == hiddenFake;
        }
        if (entity instanceof Player) {
            return true;
        }
        return module.hideDrops() && (entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof Projectile);
    }

    private @Nullable ReplayPlayer fake(int track) {
        return track >= 0 && track < fakeByTrack.length ? fakeByTrack[track] : null;
    }

    private static @Nullable ReplayPlayer present(@Nullable ReplayPlayer fake) {
        return fake != null && fake.present ? fake : null;
    }

    /**
     * State of a track for the overlay: at the current replay time, else the last recorded tick before it.
     * Returns the age in ticks of the shown state (0 = current), or -1 when the player has no data up to now.
     */
    long hudSample(int track, TrackSample out) {
        if (track == ReplayBuffer.NO_TRACK) {
            return -1;
        }
        if (buffer.sample(track, time, hudSample)) {
            out.copyFrom(hudSample);
            return 0;
        }
        long now = (long) Math.floor(time);
        long last = buffer.lastPresent(track, now, startTick);
        if (hudMissLogged.add(track)) {
            module.log("hud: no sample for %s at tick %d (%.2f s); last recorded tick in the window %d, track last written %d, window %d..%d",
                    buffer.name(track), now, elapsed(), last, buffer.lastTick(track), startTick, endTick);
        }
        if (last < 0 || !buffer.read(track, last, out)) {
            return -1;
        }
        return now - last;
    }

    @Nullable Object equipmentNow(int track, int slot) {
        return buffer.equipmentAt(track, slot, (long) Math.floor(time));
    }

    double elapsed() {
        return (time - startTick) / 20.0;
    }

    double duration() {
        return (endTick - startTick) / 20.0;
    }

    double deathAt() {
        return (deathTick - startTick) / 20.0;
    }

    double progress() {
        return endTick == startTick ? 0 : (time - startTick) / (endTick - startTick);
    }

    boolean isPlaying() {
        return playing;
    }

    double speed() {
        return speed;
    }

    CameraMode mode() {
        return mode;
    }

    String cameraNote() {
        return cameraNote;
    }

    // ---- controls ----

    void togglePause() {
        if (!playing && time >= endTick) {
            time = startTick;
            seeked = true;
        }
        playing = !playing;
        module.log("replay %s at %.2f s", playing ? "resumed" : "paused", elapsed());
    }

    void restart() {
        time = startTick;
        seeked = true;
        playing = true;
        module.log("replay restarted");
    }

    void seekProgress(double progress, boolean log) {
        seekTick(startTick + Mth.clamp(progress, 0.0, 1.0) * (endTick - startTick), log);
    }

    void seekSeconds(double delta) {
        seekTick(time + delta * 20.0, true);
    }

    private void seekTick(double tick, boolean log) {
        time = Mth.clamp(tick, startTick, endTick);
        seeked = true;
        if (log) {
            module.log("seek to %.2f s", elapsed());
        }
    }

    void changeSpeed(int direction) {
        int index = 0;
        for (int i = 0; i < SPEEDS.length; i++) {
            if (Math.abs(SPEEDS[i] - speed) < 1e-6) {
                index = i;
            }
        }
        index = Mth.clamp(index + direction, 0, SPEEDS.length - 1);
        if (direction == 0) {
            index = (index + 1) % SPEEDS.length;
        }
        speed = SPEEDS[index];
        module.log("speed -> %.2fx", speed);
    }

    void setMode(CameraMode next) {
        if (next == mode) {
            return;
        }
        if (next == CameraMode.FREE) {
            freeX = camX;
            freeY = camY;
            freeZ = camZ;
            freeYaw = camYaw;
            freePitch = camPitch;
        } else if (next != CameraMode.KILLER && mode == CameraMode.KILLER) {
            orbitYaw = camYaw;
            orbitPitch = Mth.clamp(camPitch, -30.0F, 80.0F);
            orbitInitialized = true;
        }
        module.log("camera %s -> %s", mode, next);
        mode = next;
    }

    void rotate(double dx, double dy) {
        float yaw = (float) dx * 0.4F;
        float pitch = (float) dy * 0.4F;
        if (mode == CameraMode.FREE) {
            freeYaw += yaw;
            freePitch = Mth.clamp(freePitch + pitch, -90.0F, 90.0F);
        } else if (mode != CameraMode.KILLER) {
            orbitInitialized = true;
            orbitYaw += yaw;
            orbitPitch = Mth.clamp(orbitPitch + pitch, -89.0F, 89.0F);
        }
    }

    void scroll(double amount) {
        if (mode == CameraMode.FREE) {
            freeSpeed = Mth.clamp(freeSpeed * (amount > 0 ? 1.25F : 0.8F), 1.0F, 40.0F);
        } else {
            orbitDistance = Mth.clamp(orbitDistance * (amount > 0 ? 0.85F : 1.18F), MIN_DISTANCE, MAX_DISTANCE);
        }
    }

    // ---- lifecycle ----

    /** End-of-tick checks: respawn, world change or a foreign screen abort the replay. */
    void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            stop(mc.level == null ? "disconnected" : "world changed", SCREEN_CLOSE);
        } else if (mc.player != deadPlayer) {
            stop("respawned (new player entity)", SCREEN_CLOSE);
        } else if (!deadPlayer.isDeadOrDying()) {
            stop("respawned (alive again)", SCREEN_CLOSE);
        } else if (!(mc.screen instanceof ReplayScreen)) {
            stop("screen replaced by " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()), SCREEN_KEEP);
        }
    }

    /** Leaves a replay screen whose session already ended (e.g. after an error in the frame update). */
    void leaveScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (mc.player == deadPlayer && deadPlayer.isDeadOrDying()) {
            mc.setScreen(new DeathScreen(deathMessage, hardcore, deadPlayer));
        } else {
            mc.setScreen(null);
        }
    }

    /**
     * Removes the fakes and releases all overrides. {@link #SCREEN_DEATH} (normal exit) rebuilds the death screen
     * with the message the server sent ({@code setScreen(null)} would show it without the message);
     * {@link #SCREEN_KEEP} is for calls from inside {@code Minecraft.setScreen}.
     */
    void stop(String reason, int screenAction) {
        if (current != this) {
            return;
        }
        current = null;
        for (ReplayPlayer fake : fakes) {
            level.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
        }
        Minecraft mc = Minecraft.getInstance();
        module.log(String.format(Locale.ROOT,
                "replay stopped: %s; watched %.1f s real time, %d frames, frame update avg %.1f us max %.1f us, %d events fired, %d fakes removed",
                reason, (System.currentTimeMillis() - startedMs) / 1000.0, frames, frames == 0 ? 0 : frameNanos / 1000.0 / frames,
                frameMaxNanos / 1000.0, eventsFired, fakes.size()));
        fakes.clear();
        boolean dead = mc.player == deadPlayer && deadPlayer.isDeadOrDying() && mc.level == level;
        if (screenAction == SCREEN_DEATH && dead) {
            mc.setScreen(new DeathScreen(deathMessage, hardcore, deadPlayer));
            module.log("death screen restored with the original message: \"%s\"", deathMessage == null ? "" : deathMessage.getString());
        } else if (screenAction != SCREEN_KEEP && mc.screen instanceof ReplayScreen && mc.level != null) {
            mc.setScreen(null);
            module.log("replay screen closed (%s)", dead ? "death screen without message" : "back to the game");
        }
    }
}
