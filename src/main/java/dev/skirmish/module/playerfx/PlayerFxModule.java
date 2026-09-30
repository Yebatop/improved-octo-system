package dev.skirmish.module.playerfx;

import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.Fight;
import dev.skirmish.fx.FxField;
import dev.skirmish.fx.FxPalette;
import dev.skirmish.fx.FxPresets;
import dev.skirmish.fx.FxSounds;
import dev.skirmish.fx.FxStyles;
import dev.skirmish.fx.FxWorld;
import dev.skirmish.fx.MyHits;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.ActionSetting;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * «Player FX»: cosmetics only I see. A trail behind me, an aura around me, an effect where my hits land (bigger and
 * in the crit colour on a critical hit), my own totem effect and sound (optionally without the big totem in the
 * middle of the screen, with a «−1 тотем» counter), the held item's size and place, and a cape. All of it is drawn on
 * this client from what it already knows; nothing is sent and nothing is spawned in the world. The Studio shows every
 * setting on a model.
 */
public final class PlayerFxModule extends Module {
    public static final String ID = "player_fx";
    private static @Nullable PlayerFxModule instance;

    public enum TrailWhen {
        MOVING, SPRINTING, FLYING
    }

    public enum TotemSound {
        VANILLA, SHIMMER, CHOIR, BELL, PHOENIX;

        @Nullable SoundEvent event() {
            return this == VANILLA ? null : FxSounds.of("totem_" + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public final EnumSetting<FxStyles.Trail> trail = add(new EnumSetting<>("trail", FxStyles.Trail.OFF));
    public final EnumSetting<FxPalette> trailColor = (EnumSetting<FxPalette>) add(new EnumSetting<>("trail_color", FxPalette.ACCENT)).under(trail);
    public final EnumSetting<TrailWhen> trailWhen = (EnumSetting<TrailWhen>) add(new EnumSetting<>("trail_when", TrailWhen.MOVING)).under(trail);
    public final NumberSetting trailDensity = (NumberSetting) add(new NumberSetting("trail_density", 1, 0.25, 3, 0.25).unit("×")).under(trail);

    public final EnumSetting<FxStyles.Aura> aura = add(new EnumSetting<>("aura", FxStyles.Aura.OFF));
    public final EnumSetting<FxPalette> auraColor = (EnumSetting<FxPalette>) add(new EnumSetting<>("aura_color", FxPalette.ACCENT)).under(aura);
    public final NumberSetting auraSize = (NumberSetting) add(new NumberSetting("aura_size", 1, 0.5, 1.8, 0.1).unit("×")).under(aura);
    public final BoolSetting auraFirstPerson = (BoolSetting) add(new BoolSetting("aura_first_person", false)).under(aura);

    public final EnumSetting<FxStyles.Hit> hit = add(new EnumSetting<>("hit", FxStyles.Hit.SPARKS));
    public final EnumSetting<FxPalette> hitColor = (EnumSetting<FxPalette>) add(new EnumSetting<>("hit_color", FxPalette.ACCENT)).under(hit);
    public final EnumSetting<FxPalette> critColor = (EnumSetting<FxPalette>) add(new EnumSetting<>("crit_color", FxPalette.GOLD)).under(hit);
    public final NumberSetting hitSize = (NumberSetting) add(new NumberSetting("hit_size", 1, 0.5, 2, 0.1).unit("×")).under(hit);
    public final BoolSetting hitMobs = (BoolSetting) add(new BoolSetting("hit_mobs", true)).under(hit);

    public final EnumSetting<FxStyles.Totem> totem = add(new EnumSetting<>("totem", FxStyles.Totem.GOLDEN));
    public final EnumSetting<TotemSound> totemSound = (EnumSetting<TotemSound>) add(new EnumSetting<>("totem_sound", TotemSound.SHIMMER)).under(totem);
    public final BoolSetting totemHideItem = (BoolSetting) add(new BoolSetting("totem_hide_item", false)).under(totem);
    public final BoolSetting totemCounter = (BoolSetting) add(new BoolSetting("totem_counter", true)).under(totem);
    public final BoolSetting totemOthers = (BoolSetting) add(new BoolSetting("totem_others", true)).under(totem);

    public final BoolSetting hand = add(new BoolSetting("hand", false));
    public final NumberSetting handScale = (NumberSetting) add(new NumberSetting("hand_scale", 1, 0.4, 1.6, 0.05).unit("×")).under(hand);
    public final NumberSetting handX = (NumberSetting) add(new NumberSetting("hand_x", 0, -0.6, 0.6, 0.02)).under(hand);
    public final NumberSetting handY = (NumberSetting) add(new NumberSetting("hand_y", 0, -0.6, 0.6, 0.02)).under(hand);
    public final NumberSetting handZ = (NumberSetting) add(new NumberSetting("hand_z", 0, -0.6, 0.6, 0.02)).under(hand);

    public final EnumSetting<CapeArt.Style> cape = add(new EnumSetting<>("cape", CapeArt.Style.OFF));
    public final BoolSetting capeElytra = (BoolSetting) add(new BoolSetting("cape_elytra", true)).under(cape);

    private float trailDebt;
    private float clock;
    /** My last totem pop: when, and how many totems are left. */
    long totemAt = -1;
    int totemsLeft;

    public PlayerFxModule() {
        super(ID, true);
        add(new ActionSetting("studio", () -> Minecraft.getInstance().setScreen(new dev.skirmish.studio.StudioScreen(Minecraft.getInstance().screen))));
    }

    public static @Nullable PlayerFxModule active() {
        PlayerFxModule m = instance;
        return m != null && m.isEnabled() ? m : null;
    }

    public static @Nullable PlayerFxModule instance() {
        return instance;
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
        instance = this;
        FxWorld.install();
        FxWorld.addEmitter(this::frame);
        MyHits.listen(this::onHit);
        Hud.get().register(new TotemCounterHud(this));
        CombatTracker.get().addListener(new CombatListener() {
            @Override
            public void onTotemPop(Combatant who, @Nullable Fight fight) {
                PlayerFxModule.this.onTotem(who);
            }
        });
    }

    @Override
    public void tick() {
        // The used totem leaves the inventory a moment after the pop: keep the count live while it shows.
        Minecraft mc = Minecraft.getInstance();
        if (totemAt > 0 && mc.player != null && Util.getMillis() - totemAt < 3000) {
            totemsLeft = Math.min(totemsLeft, countTotems(mc.player));
        }
    }

    // ---- trail and aura, every frame ----

    private void frame(FxField field, float dt, float pt) {
        if (!isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || me.isSpectator() || me.isInvisible()) {
            return;
        }
        clock += dt;
        Vec3 pos = me.getPosition(pt);
        boolean firstPerson = mc.options.getCameraType() == CameraType.FIRST_PERSON;
        if (aura.get() != FxStyles.Aura.OFF && (!firstPerson || auraFirstPerson.get())) {
            FxPresets.aura(field, aura.get(), auraColor.get(), pos.x, pos.y, pos.z, me.getBbHeight(), clock, dt, auraSize.getFloat());
        }
        FxStyles.Trail style = trail.get();
        if (style == FxStyles.Trail.OFF) {
            return;
        }
        Vec3 motion = me.getDeltaMovement();
        double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 20;
        boolean flying = me.isFallFlying();
        boolean on = switch (trailWhen.get()) {
            case MOVING -> speed > 1.2 || flying || Math.abs(motion.y) > 0.3;
            case SPRINTING -> me.isSprinting() || flying;
            case FLYING -> flying;
        };
        if (!on) {
            trailDebt = 0;
            return;
        }
        trailDebt += dt * FxPresets.trailRate(style) * trailDensity.getFloat() * (flying ? 1.6f : 1f);
        while (trailDebt >= 1f) {
            trailDebt -= 1f;
            double y = flying ? pos.y + 0.2 : pos.y;
            FxPresets.trail(field, style, trailColor.get(), pos.x, y, pos.z, motion.x * 20, motion.z * 20, 1f);
        }
    }

    // ---- hits ----

    private void onHit(Entity victim, boolean crit) {
        if (!isEnabled() || hit.get() == FxStyles.Hit.OFF) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(victim instanceof net.minecraft.world.entity.player.Player) && !hitMobs.get()) {
            return;
        }
        Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        Vec3 dir = at.subtract(mc.player.position());
        FxPresets.hit(FxWorld.field(), hit.get(), hitColor.get(), critColor.get(), crit, at.x, at.y, at.z, dir.x, dir.z,
                hitSize.getFloat() * Math.max(0.6f, victim.getBbWidth() / 0.6f));
    }

    // ---- totems ----

    private void onTotem(Combatant who) {
        if (!isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Entity entity = mc.level.getEntity(who.entityId());
        if (entity == null) {
            return;
        }
        boolean me = who.self();
        if (!me && (!totemOthers.get() || entity.isInvisibleTo(mc.player))) {
            return;
        }
        FxPresets.totem(FxWorld.field(), totem.get(), entity.getX(), entity.getY(), entity.getZ(), 1f);
        if (me) {
            SoundEvent sound = totemSound.get().event();
            if (sound != null) {
                FxSounds.play(sound, 1f, 0.8f);
            }
            totemAt = Util.getMillis();
            // The used one may still be counted until the server's inventory update arrives.
            totemsLeft = Math.max(0, countTotems(mc.player) - 1);
        }
    }

    private static int countTotems(LocalPlayer player) {
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    /** Whether vanilla's totem particles on {@code entity} are replaced by this module's effect. */
    public boolean replacesTotemParticles(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || totem.get() == FxStyles.Totem.VANILLA || mc.player == null) {
            return false;
        }
        return entity == mc.player || totemOthers.get() && !entity.isInvisibleTo(mc.player);
    }

    public boolean replacesTotemSound(Entity entity) {
        return isEnabled() && entity == Minecraft.getInstance().player && totemSound.get() != TotemSound.VANILLA;
    }

    public boolean hidesTotemItem() {
        return isEnabled() && totemHideItem.get();
    }

    // ---- cape ----

    /** The cape texture to wear, or null for my own. */
    public @Nullable Identifier capeTexture() {
        CapeArt.Style style = cape.get();
        return isEnabled() && style != CapeArt.Style.OFF ? CapeArt.texture(style) : null;
    }

    /** Held item offset and scale, or null when unchanged. */
    public float @Nullable [] handTransform() {
        if (!isEnabled() || !hand.get()) {
            return null;
        }
        return new float[]{handX.getFloat(), handY.getFloat(), handZ.getFloat(), handScale.getFloat()};
    }
}
