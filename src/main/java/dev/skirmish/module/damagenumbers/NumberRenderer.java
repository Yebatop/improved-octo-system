package dev.skirmish.module.damagenumbers;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the numbers in world space from {@code WorldRenderEvents.BEFORE_ENTITIES}: each one is a billboard (turned
 * to the camera like a nametag) submitted as text with the normal, depth-tested display mode, so terrain and entities
 * in front of it hide it. It rises and fades over its lifetime and pops in slightly larger.
 */
final class NumberRenderer {
    private static final String L = "layout.damage_numbers.";

    private NumberRenderer() {
    }

    static void render(DamageNumbersModule module, WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        Theme theme = Theme.get();
        CameraRenderState camera = context.worldState().cameraRenderState;
        Vec3 cam = camera.pos;
        PoseStack pose = context.matrices();
        long now = System.currentTimeMillis();
        long lifetime = module.lifetimeMs();
        float base = theme.num(L + "world_scale") * module.scale.getFloat();
        float rise = theme.num(L + "rise");
        float fadeStart = theme.num(L + "fade_start");
        float pop = theme.num(L + "pop_scale");
        float popPart = theme.num(L + "pop_part");
        Style style = Style.EMPTY.withFont(Ui.fontFor(theme.text("dn_number")));
        int outline = theme.color("dn_outline");
        for (NumberField.Num n : module.field.live()) {
            float age = n.age(now, lifetime);
            if (age >= 1f) {
                continue;
            }
            float alpha = age <= fadeStart ? 1f : 1f - (age - fadeStart) / (1f - fadeStart);
            float grow = age < popPart ? 1f + (pop - 1f) * (1f - Anim.easeOut(age / popPart)) : 1f;
            float s = base * grow;
            FormattedCharSequence text = Component.literal(n.text()).withStyle(style).getVisualOrderText();
            float width = mc.font.width(text);
            pose.pushPose();
            pose.translate(n.x - cam.x, n.y + rise * Anim.easeOut(age) - cam.y, n.z - cam.z);
            pose.mulPose(camera.orientation);
            pose.scale(s, -s, s);
            int color = withAlpha(theme.color(token(n.kind)), alpha);
            int out = (outline >>> 24) == 0 ? 0 : withAlpha(outline, alpha);
            context.commandQueue().submitText(pose, -width / 2f, 0f, text, false, Font.DisplayMode.NORMAL,
                    LightTexture.FULL_BRIGHT, color, 0, out);
            pose.popPose();
        }
    }

    static String token(NumberField.Kind kind) {
        return switch (kind) {
            case HIT -> "dn_hit";
            case CRIT -> "dn_crit";
            case TOTEM -> "dn_totem";
            case HEAL -> "dn_heal";
        };
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        // Fully transparent text is skipped by the font renderer's alpha test anyway; keep a floor so it fades out.
        return (Math.max(a, 4) << 24) | (argb & 0xFFFFFF);
    }
}
