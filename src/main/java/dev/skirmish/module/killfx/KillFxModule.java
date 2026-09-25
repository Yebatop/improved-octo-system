package dev.skirmish.module.killfx;

import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.DamageInfo;
import dev.skirmish.combat.Fight;
import dev.skirmish.combat.OwnDeath;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * «Эффекты убийства»: cosmetics for my own kills (the combat tracker's kill attribution): a kill sound, a particle
 * burst or a lightning bolt over the victim, and a banner in the middle of the screen with my kill streak (reset on
 * my death). An optional hit sound confirms my hits. Everything happens on my client only: UI sounds, client
 * particles and drawn geometry; nothing is sent and no entity is spawned. A victim invisible to me gets no effect at
 * its position and is not named on the banner.
 */
public final class KillFxModule extends Module {
    public static final String ID = "kill_fx";

    public enum KillSound {
        LEVEL_UP, ORB, PLING, BELL, CHIME, ANVIL, THUNDER;

        /** Resolved lazily so the enum can be loaded without the game's registries (menu tests). */
        SoundEvent event() {
            return switch (this) {
                case LEVEL_UP -> SoundEvents.PLAYER_LEVELUP;
                case ORB -> SoundEvents.EXPERIENCE_ORB_PICKUP;
                case PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
                case BELL -> SoundEvents.BELL_BLOCK;
                case CHIME -> SoundEvents.AMETHYST_BLOCK_CHIME;
                case ANVIL -> SoundEvents.ANVIL_LAND;
                case THUNDER -> SoundEvents.LIGHTNING_BOLT_THUNDER;
            };
        }

        float pitch() {
            return switch (this) {
                case PLING, ORB -> 1.5f;
                case ANVIL -> 1.2f;
                default -> 1f;
            };
        }
    }

    public enum HitSound {
        CLICK, CRIT, ORB, HAT;

        SoundEvent event() {
            return switch (this) {
                case CLICK -> SoundEvents.UI_BUTTON_CLICK.value();
                case CRIT -> SoundEvents.PLAYER_ATTACK_CRIT;
                case ORB -> SoundEvents.EXPERIENCE_ORB_PICKUP;
                case HAT -> SoundEvents.NOTE_BLOCK_HAT.value();
            };
        }

        float pitch() {
            return this == ORB ? 1.8f : this == HAT ? 1.4f : 1.2f;
        }
    }

    public enum Burst {
        TOTEM, FLAME, SOUL, HEARTS, SPARKS, FIREWORK;

        SimpleParticleType particle() {
            return switch (this) {
                case TOTEM -> ParticleTypes.TOTEM_OF_UNDYING;
                case FLAME -> ParticleTypes.FLAME;
                case SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
                case HEARTS -> ParticleTypes.HEART;
                case SPARKS -> ParticleTypes.END_ROD;
                case FIREWORK -> ParticleTypes.FIREWORK;
            };
        }

        /** Particle launch speed: hearts float, totem particles fly far on their own. */
        double speed() {
            return switch (this) {
                case TOTEM -> 0.6;
                case HEARTS -> 0.05;
                default -> 0.15;
            };
        }
    }

    final BoolSetting sound = add(new BoolSetting("sound", true));
    final EnumSetting<KillSound> soundType = (EnumSetting<KillSound>) add(new EnumSetting<>("sound_type", KillSound.LEVEL_UP))
            .under(sound).visibleWhen(sound::get);
    final NumberSetting volume = (NumberSetting) add(new NumberSetting("volume", 70, 5, 100, 5).unit("%"))
            .under(sound).visibleWhen(sound::get);
    final BoolSetting particles = add(new BoolSetting("particles", true));
    final EnumSetting<Burst> burst = (EnumSetting<Burst>) add(new EnumSetting<>("burst", Burst.TOTEM))
            .under(particles).visibleWhen(particles::get);
    final NumberSetting amount = (NumberSetting) add(new NumberSetting("amount", 40, 10, 120, 5))
            .under(particles).visibleWhen(particles::get);
    final BoolSetting lightning = add(new BoolSetting("lightning", false));
    final BoolSetting banner = add(new BoolSetting("banner", true));
    final BoolSetting streak = (BoolSetting) add(new BoolSetting("streak", true)).under(banner).visibleWhen(banner::get);
    final NumberSetting bannerTime = (NumberSetting) add(new NumberSetting("banner_time", 2.5, 1, 6, 0.5).unit(" s"))
            .under(banner).visibleWhen(banner::get);
    final BoolSetting hitSound = add(new BoolSetting("hit_sound", false));
    final EnumSetting<HitSound> hitSoundType = (EnumSetting<HitSound>) add(new EnumSetting<>("hit_sound_type", HitSound.CLICK))
            .under(hitSound).visibleWhen(hitSound::get);
    final NumberSetting hitVolume = (NumberSetting) add(new NumberSetting("hit_volume", 40, 5, 100, 5).unit("%"))
            .under(hitSound).visibleWhen(hitSound::get);

    final KillStreak streakCounter = new KillStreak();
    private final BoltRenderer bolts = new BoltRenderer();
    /** Last position of each fight opponent while visible to me. */
    private final Map<UUID, Vec3> lastSeen = new HashMap<>();

    /** The banner being shown: victim nick (null when not to be named), streak, when it started. */
    record Shown(@Nullable String victim, int streak, long startMs) {
    }

    private @Nullable Shown shown;

    public KillFxModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.VISUAL;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        Hud.get().register(new KillBanner(this));
        CombatTracker.get().addListener(new Listener());
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (isEnabled() && !bolts.isEmpty()) {
                bolts.render(context);
            }
        });
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> {
            lastSeen.clear();
            bolts.clear();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            streakCounter.reset();
            lastSeen.clear();
            bolts.clear();
            shown = null;
        }));
    }

    @Override
    protected void onDisable() {
        bolts.clear();
        shown = null;
    }

    long bannerMs() {
        return Math.round(bannerTime.get() * 1000);
    }

    /** The banner to show now, or null once its time is over. */
    @Nullable Shown banner(long now) {
        Shown s = shown;
        return s != null && now - s.startMs() < bannerMs() ? s : null;
    }

    private static boolean hidden(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entity != mc.player && entity.isInvisibleTo(mc.player);
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Set<UUID> active = new HashSet<>();
        for (Fight fight : CombatTracker.get().activeFights()) {
            UUID id = fight.opponent().uuid();
            active.add(id);
            Entity e = mc.level.getEntity(fight.opponent().entityId());
            if (e != null && e.getUUID().equals(id)) {
                if (hidden(e)) {
                    lastSeen.remove(id);
                } else {
                    lastSeen.put(id, e.position());
                }
            }
        }
        // Keep finished fights for a moment: the kill arrives as the fight ends.
        if (lastSeen.size() > 32) {
            lastSeen.keySet().retainAll(active);
        }
    }

    private void play(SoundEvent event, float pitch, double volumePercent) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, (float) (volumePercent / 100.0)));
    }

    /** Where the victim died, or null when it was invisible to me (then nothing is drawn there). */
    private @Nullable Vec3 victimPosition(Fight fight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Entity e = mc.level.getEntity(fight.opponent().entityId());
        if (e != null && e.getUUID().equals(fight.opponent().uuid())) {
            return hidden(e) ? null : e.position();
        }
        return lastSeen.get(fight.opponent().uuid());
    }

    private void burstAt(ClientLevel level, Vec3 pos) {
        RandomSource random = level.getRandom();
        Burst type = burst.get();
        double speed = type.speed();
        int n = amount.getInt();
        for (int i = 0; i < n; i++) {
            double vx = (random.nextDouble() - 0.5) * 2 * speed;
            double vy = random.nextDouble() * speed * 1.5;
            double vz = (random.nextDouble() - 0.5) * 2 * speed;
            level.addParticle(type.particle(), pos.x + (random.nextDouble() - 0.5) * 0.6, pos.y + 0.2 + random.nextDouble() * 1.6,
                    pos.z + (random.nextDouble() - 0.5) * 0.6, vx, vy, vz);
        }
    }

    private final class Listener implements CombatListener {
        @Override
        public void onKill(Fight fight) {
            if (!isEnabled()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            long now = System.currentTimeMillis();
            int count = streakCounter.kill(fight.opponent().name(), now);
            Vec3 pos = victimPosition(fight);
            log("kill of %s, streak %d, position %s", fight.opponent().name(), count, pos == null ? "hidden/unknown" : "known");
            if (sound.get()) {
                play(soundType.get().event(), soundType.get().pitch(), volume.get());
            }
            if (pos != null && mc.level != null) {
                if (particles.get()) {
                    burstAt(mc.level, pos);
                }
                if (lightning.get()) {
                    bolts.strike(pos.x, pos.y, pos.z, now);
                }
            }
            if (banner.get()) {
                // Name the victim only when I could see them (never an invisible player).
                shown = new Shown(pos != null ? fight.opponent().name() : null, count, now);
            }
        }

        @Override
        public void onOwnDeath(OwnDeath death) {
            streakCounter.death();
        }

        @Override
        public void onDamage(DamageInfo info) {
            if (!isEnabled() || !hitSound.get() || !info.byMe() || info.onMe()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            Entity victim = mc.level == null ? null : mc.level.getEntity(info.victim().entityId());
            if (victim == null || hidden(victim)) {
                return;
            }
            play(hitSoundType.get().event(), hitSoundType.get().pitch(), hitVolume.get());
        }
    }
}
