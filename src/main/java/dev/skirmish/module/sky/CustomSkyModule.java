package dev.skirmish.module.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.jspecify.annotations.Nullable;

/**
 * «Custom Sky»: replaces the overworld sky on your screen with another one — a nebula, the Milky Way, northern
 * lights, a quasar, a black hole, a synthwave sunset, an eternal golden hour, a blood moon, an alien world with a
 * ringed giant, or a meteor shower. The vanilla sky, sun, moon and clouds are not drawn; the preset's own gradient
 * follows day and night, its sun and moon stand where the game's are (the moon keeps its phase), and the fog takes
 * the horizon colour so the land fades into the new sky. Render-only; Feature Control id {@code custom_sky}.
 */
public final class CustomSkyModule extends Module {
    public static final String ID = "custom_sky";
    private static volatile @Nullable CustomSkyModule instance;

    public enum When {
        ALWAYS, NIGHT
    }

    final EnumSetting<SkyStyle.Look> preset = add(new EnumSetting<>("preset", SkyStyle.Look.NEBULA));
    final EnumSetting<When> when = add(new EnumSetting<>("when", When.ALWAYS));
    final BoolSetting hideClouds = add(new BoolSetting("hide_clouds", true));
    final BoolSetting fog = add(new BoolSetting("fog", true));
    final BoolSetting sunMoon = add(new BoolSetting("sun_moon", true));
    final NumberSetting intensity = (NumberSetting) add(new NumberSetting("intensity", 100, 20, 100, 5).unit("%"));

    /** The palette of the last frame (the fog of the next frame is tinted with its horizon), or null. */
    private volatile SkyStyle.@Nullable Palette palette;
    private volatile float lastCover;

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
        instance = this;
        SkyPipelines.init();
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (isEnabled()) {
                render(context);
            } else {
                palette = null;
            }
        });
    }

    // ---- hooks for the mixins ----

    /** Whether the vanilla sky (disc, sunset glow, sun, moon, stars) is skipped this frame. */
    public static boolean replacesVanillaSky() {
        CustomSkyModule m = instance;
        return m != null && m.isEnabled() && m.lastCover >= 0.999f && m.applies();
    }

    /** Whether the vanilla clouds are skipped. */
    public static boolean hidesClouds() {
        CustomSkyModule m = instance;
        return m != null && m.isEnabled() && m.hideClouds.get() && m.applies();
    }

    /** The fog colour with the new sky's horizon mixed in (ARGB in, ARGB out). */
    public static int fogColor(int vanilla) {
        CustomSkyModule m = instance;
        SkyStyle.Palette p = m == null ? null : m.palette;
        if (m == null || p == null || !m.isEnabled() || !m.fog.get() || !m.applies()) {
            return vanilla;
        }
        return (vanilla & 0xFF000000) | SkyStyle.lerp(vanilla & 0xFFFFFF, p.horizon(), m.lastCover * 0.92f);
    }

    /** The overworld, with the camera in the air and no blindness. */
    private boolean applies() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || mc.player == null) {
            return false;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera.getFluidInCamera() != FogType.NONE) {
            return false;
        }
        return !mc.player.hasEffect(MobEffects.BLINDNESS) && !mc.player.hasEffect(MobEffects.DARKNESS);
    }

    // ---- drawing ----

    private void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !applies()) {
            palette = null;
            lastCover = 0f;
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var probe = mc.gameRenderer.getMainCamera().attributeProbe();
        float sunAngle = (float) Math.toRadians(probe.getValue(EnvironmentAttributes.SUN_ANGLE, partial));
        float moonAngle = (float) Math.toRadians(probe.getValue(EnvironmentAttributes.MOON_ANGLE, partial));
        float starAngle = (float) Math.toRadians(probe.getValue(EnvironmentAttributes.STAR_ANGLE, partial));
        int phase = probe.getValue(EnvironmentAttributes.MOON_PHASE, partial).index();
        float sunY = SkyMath.body(sunAngle)[1];
        float night = SkyStyle.night(sunY);
        float dusk = SkyStyle.dusk(sunY);
        float rain = Math.min(1f, level.getRainLevel(partial) + level.getThunderLevel(partial) * 0.3f);
        float cover = when.get() == When.ALWAYS ? 1f : night;
        lastCover = cover;
        if (cover <= 0.01f) {
            palette = null;
            return;
        }
        SkyStyle.Look look = preset.get();
        SkyStyle.Palette pal = SkyStyle.at(SkyStyle.of(look), night, dusk, rain);
        palette = pal;
        float r = mc.gameRenderer.getRenderDistance() * 1.9f;
        float t = Util.getMillis() / 1000f;
        float strength = (float) (intensity.get() / 100.0) * cover;
        boolean bodies = sunMoon.get() && cover >= 0.999f;
        SkyScenes.Frame frame = new SkyScenes.Frame(r, t, night, dusk, strength, sunAngle, moonAngle, starAngle, phase, rain, pal, bodies);
        int domeAlpha = Math.round(255 * cover);
        PoseStack pose = context.matrices();
        var queue = context.commandQueue();
        // Four layers in order: the dome, light behind, opaque bodies, light in front.
        queue.order(-40).submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, c) -> SkyShapes.dome(c, p, r, pal, domeAlpha));
        queue.order(-39).submitCustomGeometry(pose, SkyPipelines.SKY, (p, c) -> SkyScenes.back(look, c, p, frame));
        if (cover >= 0.999f) {
            queue.order(-38).submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, c) -> SkyScenes.bodies(look, c, p, frame));
        }
        queue.order(-37).submitCustomGeometry(pose, SkyPipelines.SKY, (p, c) -> SkyScenes.front(look, c, p, frame));
    }
}
