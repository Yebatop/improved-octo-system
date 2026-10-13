package dev.skirmish.studio;

import dev.skirmish.fx.FxField;
import dev.skirmish.fx.FxPresets;
import dev.skirmish.fx.FxShape;
import dev.skirmish.fx.FxSounds;
import dev.skirmish.fx.FxStyles;
import dev.skirmish.module.killfx.KillFxModule;
import dev.skirmish.module.playerfx.CapeArt;
import dev.skirmish.module.playerfx.PlayerFxModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A Studio scene: me (my skin, armour, items and cape) and/or an iron-armoured mannequin on a small floor, in a pose,
 * with effects run through the same recipes as in the game, chosen by its {@link StudioLook}. A demo plays one kind of
 * effect over and over (hits, kills, totems; trails and capes on a running model), so nothing has to be clicked to see
 * it. The big scene shows both models; each style card has a little scene of its own with one model and its effect.
 * Scene space is blocks, feet at y 0; the viewer stands at −Z, so +X shows on the left.
 */
public final class StudioStage {
    public enum Pose3 {
        STAND, RUN, ATTACK, JUMP, CROUCH, FLY
    }

    public enum Scene {
        ARENA, NIGHT, SUNSET, VOID, SNOW, MEADOW
    }

    /** What plays by itself. */
    public enum Demo {
        NONE, TRAIL, AURA, HIT, KILL, TOTEM, CAPE
    }

    static final double ME_X = 0.9;
    static final double DUMMY_X = -0.9;
    static final float ME_YAW = 135f;
    static final float DUMMY_YAW = -135f;
    static final float MAX_HP = 20f;

    final FxField field;
    StudioLook look = new StudioLook();
    Pose3 pose = Pose3.STAND;
    Scene scene = Scene.ARENA;
    Demo demo = Demo.NONE;
    float clock;
    /** Camera: turn around the scene, tilt, zoom (1 = fit), and the height it looks at. */
    float yaw;
    float pitch = 10f;
    float zoom = 1f;
    float centerY = 1f;
    /** How far the floor has moved under a running model (the treadmill). */
    double scroll;

    // Layout: who is in the scene and where.
    boolean showMe = true;
    boolean showDummy = true;
    double meBaseX = ME_X;
    double dummyX = DUMMY_X;
    float meYaw = ME_YAW;
    float dummyYaw = DUMMY_YAW;
    /** Where hits come from when I'm not in the scene. */
    double hitFromX = ME_X;
    float floorRadius = 2.9f;
    boolean floorDisc = true;
    /** A card's scene makes no sound. */
    boolean silent;
    /** A card's scene beats faster and shows its effect bigger, so the little picture is never empty. */
    boolean showcase;

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
    private float demoAt = -10f;
    private int demoCount;

    StudioStage(int maxParticles) {
        field = new FxField(maxParticles);
    }

    /** The little scene of a style card: one model and the card's kind of effect, silent. */
    static StudioStage card(Demo demo) {
        StudioStage s = new StudioStage(500);
        s.demo = demo;
        s.silent = true;
        s.showcase = true;
        s.floorRadius = 1.25f;
        s.floorDisc = false;
        s.pitch = 12f;
        s.showMe = demo != Demo.HIT && demo != Demo.KILL;
        s.showDummy = !s.showMe;
        s.meBaseX = 0;
        s.dummyX = 0;
        s.dummyYaw = 180f;
        s.hitFromX = 1.5;
        switch (demo) {
            case TRAIL -> {
                s.pose = Pose3.RUN;
                s.meYaw = -90f;
            }
            case CAPE -> {
                s.pose = Pose3.RUN;
                s.meYaw = 25f;
            }
            case KILL -> s.centerY = 1.3f;
            case HIT -> s.centerY = 1.15f;
            default -> s.meYaw = 165f;
        }
        return s;
    }

    /** The pose a demo is best seen in. */
    static Pose3 poseFor(Demo demo, StudioLook look) {
        return switch (demo) {
            case TRAIL -> look.trailWhen == PlayerFxModule.TrailWhen.FLYING ? Pose3.FLY : Pose3.RUN;
            case CAPE -> Pose3.RUN;
            case HIT -> Pose3.ATTACK;
            default -> Pose3.STAND;
        };
    }

    void update(float dt) {
        clock += dt;
        boolean moving = pose == Pose3.RUN || pose == Pose3.FLY;
        double feetY = feetY();
        if (showMe && look.aura != FxStyles.Aura.OFF) {
            FxPresets.aura(field, look.aura, look.auraColor, meX(), feetY, 0, 1.8f, clock, dt, look.auraSize);
        }
        FxStyles.Trail style = look.trail;
        boolean trailOn = showMe && style != FxStyles.Trail.OFF && (demo == Demo.TRAIL || switch (look.trailWhen) {
            case MOVING -> moving || pose == Pose3.JUMP;
            case SPRINTING -> moving;
            case FLYING -> pose == Pose3.FLY;
        });
        if (trailOn) {
            double[] f = facing(meYaw);
            trailDebt += dt * FxPresets.trailRate(style) * look.trailDensity * (pose == Pose3.FLY ? 1.6f : 1f) * (showcase ? 1.8f : 1f);
            while (trailDebt >= 1f) {
                trailDebt -= 1f;
                FxPresets.trail(field, style, look.trailColor, meX(), pose == Pose3.FLY ? feetY + 0.6 : feetY, 0,
                        f[0] * 5.6, f[1] * 5.6, 1f);
            }
        } else {
            trailDebt = 0f;
        }
        if (moving) {
            // The world slides back under a model that runs on the spot.
            double[] f = facing(meYaw);
            double speed = pose == Pose3.FLY ? 9 : 5.6;
            double dx = -f[0] * speed * dt;
            double dz = -f[1] * speed * dt;
            for (var fx : field.live()) {
                fx.x += dx;
                fx.z += dz;
            }
            scroll += speed * dt;
        }
        runDemo();
        if (deadAt > 0 && clock - deadAt > 2.2f) {
            deadAt = -10f;
            dummyHp = MAX_HP;
        }
        if (deadAt < 0 && clock - lastHitAt > 4f && dummyHp < MAX_HP) {
            dummyHp = Math.min(MAX_HP, dummyHp + dt * 8);
        }
        field.update(dt);
    }

    /** The demo's beat: a hit every 0.8 s (every third a crit), a kill every 3 s, a totem every 2.5 s. */
    private void runDemo() {
        float every = switch (demo) {
            case HIT -> showcase ? 0.55f : 0.8f;
            case KILL -> showcase ? 2.4f : 3f;
            case TOTEM -> showcase ? 2f : 2.5f;
            default -> 0f;
        };
        if (every > 0 && clock - demoAt >= every) {
            demoAt = clock;
            demoCount++;
            switch (demo) {
                case HIT -> hit(demoCount % 3 == 0, false);
                case KILL -> {
                    deadAt = -10f;
                    dummyHp = MAX_HP;
                    swingAt = clock;
                    kill(false);
                }
                case TOTEM -> totem(false);
                default -> {
                }
            }
        } else if (demo == Demo.NONE && pose == Pose3.ATTACK && clock - swingAt > 0.7f) {
            hit(clock % 2.1f < 0.7f, true);
        }
    }

    /** Starts a demo now (the first beat right away). */
    void startDemo(Demo next) {
        if (demo != next) {
            demo = next;
            demoAt = -10f;
            demoCount = 0;
        }
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
        return pose == Pose3.FLY && showDummy ? meBaseX + 0.8 : meBaseX;
    }

    boolean dummyDead() {
        return deadAt > 0 && clock - deadAt < 1.4f;
    }

    boolean dummyGone() {
        return deadAt > 0 && clock - deadAt >= 1.4f;
    }

    // ---- actions ----

    /** A hit on the mannequin; {@code sound} plays my hit sound (the buttons do, demos don't). */
    void hit(boolean crit, boolean sound) {
        swingAt = clock;
        if (dummyDead() || dummyGone() || !showDummy) {
            return;
        }
        if (look.hit != FxStyles.Hit.OFF) {
            double from = showMe ? meX() : hitFromX;
            // On the side facing the attacker (and a little towards the viewer), not inside the body that would hide it.
            double side = Math.signum(from - dummyX);
            FxPresets.hit(field, look.hit, look.hitColor, look.critColor, crit, dummyX + side * 0.38, 1.15, -0.18, dummyX - from, 0,
                    look.hitSize * (showcase ? 1.4f : 1f));
        }
        KillFxModule kfx = KillFxModule.instance();
        if (sound && !silent && kfx != null && kfx.hitSoundOn()) {
            kfx.playHitSound(crit);
        }
        hurtAt = clock;
        lastHitAt = clock;
        dummyHp -= crit ? 6f : 4f;
        if (dummyHp <= 0) {
            if (demo == Demo.HIT) {
                dummyHp = MAX_HP;
            } else {
                dummyHp = 0;
                kill(sound);
            }
        }
    }

    void kill(boolean sound) {
        swingAt = clock;
        if (dummyGone() || !showDummy) {
            return;
        }
        // Only kills by hand make a streak; the demo's own kills don't count up.
        streak = sound && clock - lastKillAt < 8f ? streak + 1 : 1;
        lastKillAt = clock;
        deadAt = clock;
        hurtAt = clock;
        dummyHp = 0;
        KillFxModule kfx = KillFxModule.instance();
        if (sound && !silent && kfx != null && kfx.killSoundOn()) {
            kfx.playKillSound(streak);
        }
        if (look.killParticles) {
            FxStyles.Kill fx = look.burst.fx();
            if (fx != null) {
                FxPresets.kill(field, fx, look.burstColor, dummyX, 0, 0, look.burstScale);
            } else {
                vanillaLike(look.burst, look.burstScale);
            }
        }
        if (look.lightning) {
            boltAt = clock;
            boltSeed = RandomSource.create().nextLong();
        }
    }

    /** The vanilla-particle bursts drawn with effect particles of the same look (the Studio has no world). */
    private void vanillaLike(KillFxModule.Burst burst, float scale) {
        var r = RandomSource.create();
        int n = Math.round(40 * scale);
        for (int i = 0; i < n; i++) {
            double vx = (r.nextDouble() - 0.5) * 2;
            double vz = (r.nextDouble() - 0.5) * 2;
            double x = dummyX + (r.nextDouble() - 0.5) * 0.6;
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

    void totem(boolean sound) {
        if (!showMe) {
            return;
        }
        FxStyles.Totem style = look.totem;
        if (style == FxStyles.Totem.VANILLA) {
            var r = RandomSource.create();
            for (int i = 0; i < 40; i++) {
                field.spawn(FxShape.DOT).at(meX(), feetY() + 1, 0).vel((r.nextDouble() - 0.5) * 6, r.nextDouble() * 5, (r.nextDouble() - 0.5) * 6)
                        .gravity(5f).drag(0.35f).life(1.2f).size(0.05f, 0.02f).fade(r.nextBoolean() ? 0xFFE8D33F : 0xFF7CD85A);
            }
        } else {
            FxPresets.totem(field, style, meX(), feetY(), 0, 1f);
        }
        PlayerFxModule pfx = PlayerFxModule.instance();
        if (sound && !silent && pfx != null) {
            var event = pfx.totemSound.get().event();
            FxSounds.play(event == null ? SoundEvents.TOTEM_USE : event, 1f, event == null ? 0.6f : 0.8f);
        }
    }

    void streakOfThree() {
        for (int i = 0; i < 3; i++) {
            final int k = i;
            field.later(i * 0.9f, () -> {
                deadAt = -10f;
                dummyHp = MAX_HP;
                kill(true);
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

    /** My model in the current pose wearing the look's cape, or null when there's nothing to draw it from. */
    @Nullable AvatarRenderState me(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        AvatarRenderState state;
        PlayerSkin own;
        if (mc.player != null) {
            EntityRenderState s = dispatcher.getRenderer(mc.player).createRenderState(mc.player, partialTick);
            if (!(s instanceof AvatarRenderState a)) {
                return null;
            }
            state = a;
            own = mc.player.getSkin();
        } else {
            state = new AvatarRenderState();
            state.entityType = EntityType.PLAYER;
            own = mc.getSkinManager().createLookup(mc.getGameProfile(), false).get();
        }
        // The look's cape (or my own one), whatever the settings say: cards and hovering try capes on.
        if (look.cape != CapeArt.Style.OFF) {
            Identifier texture = CapeArt.texture(look.cape);
            ClientAsset.Texture cape = new ClientAsset.ResourceTexture(texture, texture);
            state.skin = new PlayerSkin(own.body(), cape, look.capeElytra ? cape : own.elytra(), own.model(), own.secure());
            state.showCape = true;
        } else {
            state.skin = own;
        }
        prepare(state, meYaw);
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
            default -> state.walkAnimationPos = 0;
        }
        return state;
    }

    /** The mannequin: iron armour, a hurt tint just after a hit, falling over when killed. */
    AvatarRenderState dummy() {
        AvatarRenderState state = new AvatarRenderState();
        state.entityType = EntityType.PLAYER;
        state.skin = ClientMannequin.DEFAULT_SKIN;
        prepare(state, dummyYaw);
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
}
