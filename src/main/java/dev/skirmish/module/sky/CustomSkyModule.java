package dev.skirmish.module.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;

/**
 * «Custom Sky»: a sky that exists only on your screen — a starfield with the galaxy band, northern lights, a golden
 * sunset glow or a quasar with an accretion disk and jets. Drawn additively behind the terrain (depth-tested), mostly
 * at night (fading in at dusk), weaker in rain; the overworld only. Render-only; Feature Control id
 * {@code custom_sky}.
 */
public final class CustomSkyModule extends Module {
    public static final String ID = "custom_sky";

    public enum Preset {
        GALAXY, AURORA, QUASAR, SUNSET
    }

    final EnumSetting<Preset> preset = add(new EnumSetting<>("preset", Preset.QUASAR));
    final BoolSetting byDay = add(new BoolSetting("by_day", false));
    final NumberSetting intensity = (NumberSetting) add(new NumberSetting("intensity", 100, 20, 100, 5).unit("%"));

    public CustomSkyModule() {
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
        SkyPipelines.init();
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (isEnabled()) {
                render(context);
            }
        });
    }

    private void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float night = byDay.get() ? 1f : SkyMath.night(level.getDayTime());
        float strength = night * (1f - level.getRainLevel(partial) * 0.85f) * (float) (intensity.get() / 100.0);
        Preset p = preset.get();
        if (p == Preset.SUNSET) {
            float dusk = byDay.get() ? 1f : Math.max(night, 1f - Math.abs(night - 0.5f) * 2f);
            strength = Math.max(strength, dusk * (float) (intensity.get() / 100.0) * (1f - level.getRainLevel(partial)));
        }
        if (strength <= 0.01f) {
            return;
        }
        float r = mc.gameRenderer.getRenderDistance() * 2f;
        float t = Util.getMillis() / 1000f;
        float s = strength;
        PoseStack pose = context.matrices();
        pose.pushPose();
        context.commandQueue().submitCustomGeometry(pose, SkyPipelines.SKY, (pp, c) -> {
            SkyPainter.stars(c, pp, r, t, s * (p == Preset.SUNSET ? 0.5f : 1f));
            switch (p) {
                case GALAXY -> SkyPainter.galaxy(c, pp, r, s);
                case AURORA -> SkyPainter.aurora(c, pp, r, t, s);
                case QUASAR -> {
                    SkyPainter.galaxy(c, pp, r, s * 0.6f);
                    SkyPainter.quasar(c, pp, r, t, s);
                }
                case SUNSET -> SkyPainter.sunset(c, pp, r, s);
            }
        });
        pose.popPose();
    }
}
