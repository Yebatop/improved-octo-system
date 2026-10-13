package dev.skirmish.studio;

import dev.skirmish.fx.FxPalette;
import dev.skirmish.fx.FxStyles;
import dev.skirmish.module.killfx.KillFxModule;
import dev.skirmish.module.playerfx.CapeArt;
import dev.skirmish.module.playerfx.PlayerFxModule;

/**
 * Which effects a Studio scene shows: read from the Player FX and Kill FX settings, then changed in place for what the
 * mouse is over (a style card, a colour) or narrowed to one kind of effect for a card's own little scene.
 */
final class StudioLook {
    FxStyles.Trail trail = FxStyles.Trail.OFF;
    FxPalette trailColor = FxPalette.AUTO;
    float trailDensity = 1f;
    PlayerFxModule.TrailWhen trailWhen = PlayerFxModule.TrailWhen.MOVING;
    FxStyles.Aura aura = FxStyles.Aura.OFF;
    FxPalette auraColor = FxPalette.AUTO;
    float auraSize = 1f;
    FxStyles.Hit hit = FxStyles.Hit.SPARKS;
    FxPalette hitColor = FxPalette.AUTO;
    FxPalette critColor = FxPalette.AUTO;
    float hitSize = 1f;
    boolean killParticles = true;
    KillFxModule.Burst burst = KillFxModule.Burst.TOTEM;
    FxPalette burstColor = FxPalette.AUTO;
    float burstScale = 1f;
    boolean lightning;
    FxStyles.Totem totem = FxStyles.Totem.GOLDEN;
    CapeArt.Style cape = CapeArt.Style.OFF;
    boolean capeElytra = true;

    /** The current settings. */
    static StudioLook current() {
        StudioLook l = new StudioLook();
        PlayerFxModule p = PlayerFxModule.instance();
        if (p != null) {
            l.trail = p.trail.get();
            l.trailColor = p.trailColor.get();
            l.trailDensity = p.trailDensity.getFloat();
            l.trailWhen = p.trailWhen.get();
            l.aura = p.aura.get();
            l.auraColor = p.auraColor.get();
            l.auraSize = p.auraSize.getFloat();
            l.hit = p.hit.get();
            l.hitColor = p.hitColor.get();
            l.critColor = p.critColor.get();
            l.hitSize = p.hitSize.getFloat();
            l.totem = p.totem.get();
            l.cape = p.cape.get();
            l.capeElytra = p.capeElytra.get();
        }
        KillFxModule k = KillFxModule.instance();
        if (k != null) {
            l.killParticles = k.particles.get();
            l.burst = k.burst.get();
            l.burstColor = k.burstColor.get();
            l.burstScale = Math.max(0.5f, Math.min(2f, k.burstScale()));
            l.lightning = k.lightning.get();
        }
        return l;
    }

    /** Only the effects of one kind kept (a card's scene shows just its own effect). */
    StudioLook only(StudioStage.Demo demo) {
        if (demo != StudioStage.Demo.TRAIL) {
            trail = FxStyles.Trail.OFF;
        }
        if (demo != StudioStage.Demo.AURA) {
            aura = FxStyles.Aura.OFF;
        }
        if (demo != StudioStage.Demo.HIT) {
            hit = FxStyles.Hit.OFF;
        }
        if (demo != StudioStage.Demo.KILL) {
            killParticles = false;
            lightning = false;
        }
        if (demo != StudioStage.Demo.CAPE) {
            cape = CapeArt.Style.OFF;
        }
        return this;
    }
}
