package dev.skirmish.studio;

import dev.skirmish.fx.FxField;
import dev.skirmish.fx.FxPalette;
import dev.skirmish.fx.FxPresets;
import dev.skirmish.fx.FxShape;
import dev.skirmish.fx.FxStyles;
import dev.skirmish.module.killfx.KillFxModule;
import dev.skirmish.module.playerfx.PlayerFxModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What the Studio shows: me (my skin, armour, items and cape) and a mannequin in iron armour on a small floor, in a
 * pose, with the Player FX and Kill FX effects running on them through the same recipes as in the game. Scene space
 * is blocks, feet at y 0; the viewer stands at −Z, so me at x +0.9 shows on the left and the mannequin at x −0.9 on the
 * right, both turned half towards the viewer.
 */
public final class StudioStage {
    public enum Pose3 {
        STAND, RUN, ATTACK, JUMP, CROUCH, FLY
    }

    public enum Scene {
        ARENA, NIGHT, SUNSET, VOID, SNOW, MEADOW
    }

    static final double ME_X = 0.9;
    static final double DUMMY_X = -0.9;
    static final float ME_YAW = 135f;
    static final float DUMMY_YAW = -135f;
    static final float MAX_HP = 20f;

    final FxField field = new FxField(1800);
    Pose3 pose = Pose3.STAND;
    Scene scene = Scene.ARENA;
    float clock;
    /** Camera: turn around the scene, tilt, zoom (1 = fit). */
    float yaw;
    float pitch = 10f;
    float zoom = 1f;
    /** How far the floor has moved under a running model (the treadmill). */
    double scroll;

    float dummyHp = MAX_HP;
    float hurtAt = -10f;
    float deadAt = -10f;
    float lastHitAt = -10f;
    int streak;
    float lastKillAt = -100f;
    float boltAt = -10f;
    long boltSeed;
    private float trailDebt;
    private float swingAt = -10f;

    void update(float dt) {
        clock += dt;
        PlayerFxModule pfx = PlayerFxModule.instance();
        boolean moving = pose == Pose3.RUN || pose == Pose3.FLY;
        double feetY = feetY();
        if (pfx != null) {
            if (pfx.aura.get() != FxStyles.Aura.OFF) {
                FxPresets.aura(field, pfx.aura.get(), pfx.auraColor.get(), meX(), feetY, 0, 1.8f, clock, dt, pfx.auraSize.getFloat());
            }
            FxStyles.Trail style = pfx.trail.get();
            boolean trailOn = style != FxStyles.Trail.OFF && switch (pfx.trailWhen.get()) {
                case MOVING -> moving || pose == Pose3.JUMP;
                case SPRINTING -> moving;
                case FLYING -> pose == Pose3.FLY;
            };
            if (trailOn) {
                double[] f = facing(ME_YAW);
                trailDebt += dt * FxPresets.trailRate(style) * pfx.trailDensity.getFloat() * (pose == Pose3.FLY ? 1.6f : 1f);
                while (trailDebt >= 1f) {
                    trailDebt -= 1f;
                    FxPresets.trail(field, style, pfx.trailColor.get(), meX(), pose == Pose3.FLY ? feetY + 0.6 : feetY, 0,
                            f[0] * 5.6, f[1] * 5.6, 1f);
                }
            } else {
                trailDebt = 0f;
            }
        }
        if (moving) {
            // The world slides back under a model that runs on the spot.
            double[] f = facing(ME_YAW);
            double speed = pose == Pose3.FLY ? 9 : 5.6;
            double dx = -f[0] * speed * dt;
            double dz = -f[1] * speed * dt;
            for (var fx : field.live()) {
                fx.x += dx;
                fx.z += dz;
            }
            scroll += speed * dt;
        }
        if (pose == Pose3.ATTACK && clock - swingAt > 0.7f) {
            hit(clock % 2.1f < 0.7f);
        }
        if (deadAt > 0 && clock - deadAt > 2.2f) {
            deadAt = -10f;
            dummyHp = MAX_HP;
        }
        if (deadAt < 0 && clock - lastHitAt > 4f && dummyHp < MAX_HP) {
            dummyHp = Math.min(MAX_HP, dummyHp + dt * 8);
        }
        field.update(dt);
    }

    /** MC yaw → facing direction {x, z} (yaw 0 faces +Z). */
    static double[] facing(float yawDegrees) {
        double y = Math.toRadians(yawDegrees);
        return new double[]{-Math.sin(y), Math.cos(y)};
    }

    double feetY() {
        if (pose == Pose3.JUMP) {
            float t = (clock % 0.9f) / 0.9f;
            return 4 * t * (1 - t) * 0.9;
        }
        return pose == Pose3.FLY ? 0.9 : 0;
    }

    /** Where my model stands: a little aside while flying so the wings clear the mannequin. */
    double meX() {
        return pose == Pose3.FLY ? ME_X + 0.8 : ME_X;
    }

    boolean dummyDead() {
        return deadAt > 0 && clock - deadAt < 1.4f;
    }

    boolean dummyGone() {
        return deadAt > 0 && clock - deadAt >= 1.4f;
    }

    // ---- actions ----

    void hit(boolean crit) {
        swingAt = clock;
        if (dummyDead() || dummyGone()) {
            return;
        }
        PlayerFxModule pfx = PlayerFxModule.instance();
        if (pfx != null && pfx.hit.get() != FxStyles.Hit.OFF) {
            FxPresets.hit(field, pfx.hit.get(), pfx.hitColor.get(), pfx.critColor.get(), crit, DUMMY_X, 1.1, 0,
                    DUMMY_X - ME_X, 0, pfx.hitSize.getFloat());
        }
        KillFxModule kfx = KillFxModule.instance();
        if (kfx != null && kfx.hitSoundOn()) {
            kfx.playHitSound(crit);
        }
        hurtAt = clock;
        lastHitAt = clock;
        dummyHp -= crit ? 6f : 4f;
        if (dummyHp <= 0) {
            dummyHp = 0;
            kill();
        }
    }

    void kill() {
        swingAt = clock;
        if (dummyGone()) {
            return;
        }
        streak = clock - lastKillAt < 8f ? streak + 1 : 1;
        lastKillAt = clock;
        deadAt = clock;
        hurtAt = clock;
        dummyHp = 0;
        KillFxModule kfx = KillFxModule.instance();
        if (kfx == null) {
            return;
        }
        if (kfx.killSoundOn()) {
            kfx.playKillSound(streak);
        }
        if (kfx.particlesOn()) {
            FxStyles.Kill fx = kfx.burst().fx();
            float scale = Math.max(0.5f, Math.min(2f, kfx.burstScale()));
            if (fx != null) {
                FxPresets.kill(field, fx, kfx.burstColor(), DUMMY_X, 0, 0, scale);
            } else {
                vanillaLike(kfx.burst(), scale);
            }
        }
        if (kfx.lightningOn()) {
            boltAt = clock;
            boltSeed = net.minecraft.util.RandomSource.create().nextLong();
        }
    }

    /** The vanilla-particle bursts drawn with effect particles of the same look (the Studio has no world). */
    private void vanillaLike(KillFxModule.Burst burst, float scale) {
        var r = net.minecraft.util.RandomSource.create();
        int n = Math.round(40 * scale);
        for (int i = 0; i < n; i++) {
            double vx = (r.nextDouble() - 0.5) * 2;
            double vz = (r.nextDouble() - 0.5) * 2;
            double x = DUMMY_X + (r.nextDouble() - 0.5) * 0.6;
            double y = 0.2 + r.nextDouble() * 1.6;
            double z = (r.nextDouble() - 0.5) * 0.6;
            switch (burst) {
                case TOTEM -> field.spawn(FxShape.DOT).at(x, y, z).vel(vx * 4, r.nextDouble() * 5, vz * 4).gravity(6f).drag(0.4f)
                        .life(1.2f).size(0.05f, 0.02f).fade(r.nextBoolean() ? 0xFFE8D33F : 0xFF7CD85A);
                case FLAME -> field.spawn(FxShape.GLOW).at(x, y, z).vel(vx * 0.5, 0.6, vz * 0.5).life(0.8f).size(0.1f, 0f)
                        .color(0xFFFFD166, 0x00FF6A00);
                case SOUL -> field.spawn(FxShape.GLOW).at(x, y, z).vel(vx * 0.5, 0.6, vz * 0.5).life(0.8f).size(0.1f, 0f)
                        .color(0xFFB5F4FF, 0x002FD8FF);
                case HEARTS -> field.spawn(FxShape.STAR).at(x, y, z).vel(0, 0.4, 0).life(1f).size(0.12f, 0.05f).fade(0xFFFF4D6D);
                case SPARKS -> field.spawn(FxShape.DOT).at(x, y, z).vel(vx, 0.4, vz).life(1.2f).size(0.04f, 0f).fade(0xFFFFFFFF);
                default -> field.spawn(FxShape.SPARK).at(x, y, z).vel(vx * 2, r.nextDouble() * 2, vz * 2).drag(0.3f).life(1f)
                        .size(0.03f, 0f).fade(0xFFFFFFFF);
            }
        }
    }

    void totem() {
        PlayerFxModule pfx = PlayerFxModule.instance();
        if (pfx == null) {
            return;
        }
        FxStyles.Totem style = pfx.totem.get();
        if (style == FxStyles.Totem.VANILLA) {
            var r = net.minecraft.util.RandomSource.create();
            for (int i = 0; i < 40; i++) {
                field.spawn(FxShape.DOT).at(meX(), feetY() + 1, 0).vel((r.nextDouble() - 0.5) * 6, r.nextDouble() * 5, (r.nextDouble() - 0.5) * 6)
                        .gravity(5f).drag(0.35f).life(1.2f).size(0.05f, 0.02f).fade(r.nextBoolean() ? 0xFFE8D33F : 0xFF7CD85A);
            }
            dev.skirmish.fx.FxSounds.play(net.minecraft.sounds.SoundEvents.TOTEM_USE, 1f, 0.6f);
        } else {
            FxPresets.totem(field, style, meX(), feetY(), 0, 1f);
            var sound = pfx.totemSound.get();
            if (sound == PlayerFxModule.TotemSound.VANILLA) {
                dev.skirmish.fx.FxSounds.play(net.minecraft.sounds.SoundEvents.TOTEM_USE, 1f, 0.6f);
            } else {
                dev.skirmish.fx.FxSounds.play(dev.skirmish.fx.FxSounds.of("totem_" + sound.name().toLowerCase(java.util.Locale.ROOT)), 1f, 0.8f);
            }
        }
    }

    void streakOfThree() {
        for (int i = 0; i < 3; i++) {
            final int k = i;
            field.later(i * 0.9f, () -> {
                deadAt = -10f;
                dummyHp = MAX_HP;
                kill();
                if (k < 2) {
                    field.later(0.8f, () -> {
                        deadAt = -10f;
                        dummyHp = MAX_HP;
                    });
                }
            });
        }
    }

    // ---- models ----

    /** My model in the current pose, or null when there's nothing to draw it from. */
    @Nullable AvatarRenderState me(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        AvatarRenderState state;
        if (mc.player != null) {
            EntityRenderState s = dispatcher.getRenderer(mc.player).createRenderState(mc.player, partialTick);
            if (!(s instanceof AvatarRenderState a)) {
                return null;
            }
            state = a;
        } else {
            state = new AvatarRenderState();
            state.entityType = EntityType.PLAYER;
            state.skin = mc.getSkinManager().createLookup(mc.getGameProfile(), false).get();
            // Outside a world there is no player entity for the cape hook: put the chosen cape on here.
            PlayerFxModule pfx = PlayerFxModule.active();
            var texture = pfx == null ? null : pfx.capeTexture();
            if (texture != null) {
                var cape = new net.minecraft.core.ClientAsset.ResourceTexture(texture, texture);
                state.skin = new net.minecraft.world.entity.player.PlayerSkin(state.skin.body(), cape,
                        pfx.capeElytra.get() ? cape : state.skin.elytra(), state.skin.model(), state.skin.secure());
                state.showCape = true;
            }
        }
        prepare(state, ME_YAW);
        state.isInvisible = false;
        state.isInvisibleToPlayer = false;
        state.nameTag = null;
        state.scoreText = null;
        state.parrotOnLeftShoulder = null;
        state.parrotOnRightShoulder = null;
        state.isCrouching = pose == Pose3.CROUCH;
        state.pose = pose == Pose3.CROUCH ? Pose.CROUCHING : Pose.STANDING;
        state.isFallFlying = false;
        state.fallFlyingTimeInTicks = 0;
        state.walkAnimationSpeed = 0;
        state.attackTime = 0;
        state.capeFlap = 0;
        state.capeLean = 0;
        state.capeLean2 = 0;
        switch (pose) {
            case RUN -> {
                state.walkAnimationPos = clock * 20 * 0.8f;
                state.walkAnimationSpeed = 0.75f;
                state.capeLean = 50f;
                state.capeFlap = (float) Math.sin(clock * 12) * 6;
                state.xRot = 8;
            }
            case JUMP -> {
                state.capeLean = 20f;
                state.walkAnimationSpeed = 0.2f;
                state.walkAnimationPos = clock * 8;
            }
            case ATTACK -> {
                float t = clock - swingAt;
                state.attackTime = t < 0.3f ? t / 0.3f : 0f;
                state.attackArm = HumanoidArm.RIGHT;
            }
            case FLY -> {
                state.pose = Pose.FALL_FLYING;
                state.isFallFlying = true;
                state.fallFlyingTimeInTicks = 40;
                state.xRot = -20;
                state.elytraRotX = 0.35f;
                state.elytraRotZ = -1.4f;
                state.capeLean = 70f;
                if (!state.chestEquipment.is(Items.ELYTRA)) {
                    state.chestEquipment = new ItemStack(Items.ELYTRA);
                }
            }
            default -> {
                state.walkAnimationPos = 0;
            }
        }
        return state;
    }

    /** The mannequin: iron armour, a hurt tint just after a hit, falling over when killed. */
    AvatarRenderState dummy() {
        AvatarRenderState state = new AvatarRenderState();
        state.entityType = EntityType.PLAYER;
        state.skin = ClientMannequin.DEFAULT_SKIN;
        prepare(state, DUMMY_YAW);
        state.headEquipment = new ItemStack(Items.IRON_HELMET);
        state.chestEquipment = new ItemStack(Items.IRON_CHESTPLATE);
        state.legsEquipment = new ItemStack(Items.IRON_LEGGINGS);
        state.feetEquipment = new ItemStack(Items.IRON_BOOTS);
        state.hasRedOverlay = clock - hurtAt < 0.3f;
        if (dummyDead()) {
            state.deathTime = Math.min(20f, (clock - deadAt) * 20f);
            state.hasRedOverlay = true;
        }
        state.showCape = false;
        return state;
    }

    private void prepare(AvatarRenderState state, float bodyYaw) {
        state.lightCoords = 0xF000F0;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.bodyRot = bodyYaw;
        state.yRot = 0;
        state.xRot = 0;
        state.ageInTicks = clock * 20;
        state.boundingBoxWidth = 0.6f;
        state.boundingBoxHeight = 1.8f;
        state.eyeHeight = 1.62f;
        state.scale = 1f;
        state.deathTime = 0;
        state.hasRedOverlay = false;
    }

    /** Scene colours: background top and bottom, floor, floor lines, glow ring. */
    static int[] colors(Scene scene) {
        return switch (scene) {
            case ARENA -> new int[]{0xFF1A1233, 0xFF07060D, 0xF0141020, 0x3C7C5CFF, 0xFF7C5CFF};
            case NIGHT -> new int[]{0xFF0A1740, 0xFF02040C, 0xF00B1226, 0x305A8CFF, 0xFF5AC8FA};
            case SUNSET -> new int[]{0xFFFF8A5B, 0xFF4A1F4F, 0xF02A1428, 0x40FFB36B, 0xFFFF7E5F};
            case VOID -> new int[]{0xFF000000, 0xFF000000, 0x00000000, 0x00000000, 0xFF9B6BFF};
            case SNOW -> new int[]{0xFFDDE8F5, 0xFFB9C9DC, 0xF8F2F6FB, 0x30000000, 0xFF8FB8FF};
            case MEADOW -> new int[]{0xFF8EC5FF, 0xFFD7ECFF, 0xFF5E9E3A, 0x28000000, 0xFFFFFFFF};
        };
    }

    FxPalette accent() {
        return FxPalette.ACCENT;
    }
}
