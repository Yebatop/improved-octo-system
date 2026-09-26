package dev.skirmish.module.nametag;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skirmish.debug.DebugLog;
import dev.skirmish.module.friends.FriendsModule;
import dev.skirmish.module.survival.PvpState;
import dev.skirmish.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Optional;

/**
 * Called from {@code AvatarRendererMixin} at the start of {@code AvatarRenderer.submitNameTag}: decorates the nametag
 * vanilla is about to draw (friend color, inline HP) and submits the HP line through the same
 * {@code submitNameTag} call vanilla uses, with the same see-through flag. Nothing happens when vanilla shows no
 * nametag, for the local player, or for a player invisible to the local player.
 */
public final class NametagHooks {
    /** Vanilla's step between the score line and the name line ({@code AvatarRenderer.submitNameTag}). */
    private static final float LINE_STEP = 9.0F * 1.15F * 0.025F;
    private static final float NAMETAG_LIFT = 0.5F;
    private static final String L = "layout.nametag.";
    /** The name tag this state already carries our changes in (guards against a second submit of one state). */
    private static final RenderStateDataKey<Component> DECORATED = RenderStateDataKey.create(() -> "skirmish:nametag_decorated");
    private static boolean failureLogged;

    private NametagHooks() {
    }

    /** Returns true when vanilla should skip this nametag (a Skirmish HP plate shows the player instead). */
    public static boolean onSubmitNameTag(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                          CameraRenderState camera) {
        try {
            if (NametagHpModule.replacesNameTag(state.id)) {
                return true;
            }
            submit(state, poseStack, collector, camera);
        } catch (Throwable t) {
            if (!failureLogged) {
                failureLogged = true;
                DebugLog.error(NametagHpModule.ID, "nametag decoration failed", t);
            }
        }
        return false;
    }

    private static void submit(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Component nameTag = state.nameTag;
        Vec3 attachment = state.nameTagAttachment;
        if (nameTag == null || attachment == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof Player player) || player == mc.player) {
            return;
        }
        boolean invisible = player.isInvisibleTo(mc.player);
        if (!NametagPolicy.decorate(true, invisible)) {
            return;
        }
        boolean fresh = state.getData(DECORATED) != nameTag;

        NametagHpModule hp = NametagHpModule.instance();
        MutableComponent line = null;
        float fraction = 0f;
        NametagHpModule.Position position = NametagHpModule.Position.ABOVE;
        if (hp != null && hp.isEnabled() && !hp.skirmishStyle()) {
            boolean opponent = hp.show.get() != NametagPolicy.Show.ALWAYS && PvpState.fightingWith(player.getUUID());
            if (NametagPolicy.showHp(true, invisible, hp.show.get(), hp.inPvp(), opponent)) {
                fraction = HpText.fraction(player.getHealth(), player.getMaxHealth());
                line = hpLine(hp, player, fraction);
                position = hp.position.get();
            }
        }

        if (fresh) {
            Component decorated = nameTag;
            int friendColor = FriendsModule.nametagColor(player);
            if (friendColor >= 0) {
                decorated = FriendStyle.recolor(decorated, player.getGameProfile().name(), friendColor,
                        FriendsModule.nametagMarker(), Theme.get().string("nametag.friend_marker"));
            }
            if (line != null && position == NametagHpModule.Position.INLINE) {
                decorated = Component.empty().append(decorated).append(" ").append(line);
            }
            if (decorated != nameTag) {
                state.nameTag = decorated;
                state.setData(DECORATED, decorated);
            }
        }
        if (line == null || position == NametagHpModule.Position.INLINE) {
            return;
        }

        int yOffset = state.showExtraEars ? -10 : 0;
        float lines = position == NametagHpModule.Position.ABOVE ? (state.scoreText != null ? 2 : 1) : -1;
        boolean seeThrough = !state.isDiscrete;
        poseStack.pushPose();
        poseStack.translate(0.0F, lines * LINE_STEP, 0.0F);
        collector.submitNameTag(poseStack, attachment, yOffset, line, seeThrough, state.lightCoords, state.distanceToCameraSq, camera);
        if (hp.bar.get()) {
            submitBar(state, poseStack, collector, camera, attachment, yOffset, fraction, position, seeThrough);
        }
        poseStack.popPose();
    }

    /** "17.5 ❤ +4": value and heart in the HP color, absorption in gold. */
    private static MutableComponent hpLine(NametagHpModule hp, Player player, float fraction) {
        Theme theme = Theme.get();
        char separator = Minecraft.getInstance().options.languageCode.startsWith("en") ? '.' : ',';
        int color = HpText.color(fraction, theme.color("bad"), theme.color("warn"), theme.color("good")) & 0xFFFFFF;
        Style style = Style.EMPTY.withColor(color);
        MutableComponent line = Component.literal(HpText.value(player.getHealth(), hp.units.get(), separator) + " " + HpText.HEART)
                .withStyle(style);
        if (hp.absorption.get()) {
            String extra = HpText.absorption(player.getAbsorptionAmount(), hp.units.get(), separator);
            if (!extra.isEmpty()) {
                line.append(Component.literal(" " + extra).withStyle(Style.EMPTY.withColor(theme.color("nametag_absorption") & 0xFFFFFF)));
            }
        }
        return line;
    }

    /**
     * Small bar right above (line above the nick) or below (line below) the HP text, in the nametag's own billboard
     * space. Two side-by-side quads (filled part, empty part) so draw order never matters; like the nametag text it
     * gets a depth-tested pass, plus a dimmed see-through pass only when vanilla's nametag has one (not sneaking).
     */
    private static void submitBar(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                  CameraRenderState camera, Vec3 attachment, int yOffset, float fraction,
                                  NametagHpModule.Position position, boolean seeThrough) {
        Theme theme = Theme.get();
        float width = theme.num(L + "bar_width");
        float height = theme.num(L + "bar_height");
        float gap = theme.num(L + "bar_gap");
        float top = position == NametagHpModule.Position.ABOVE ? yOffset - 1 - gap - height : yOffset + 9 + gap;
        float left = -width / 2f;
        float split = left + width * fraction;
        int fill = HpText.color(fraction, theme.color("bad"), theme.color("warn"), theme.color("good"));
        int track = theme.color("nametag_bar_track");

        poseStack.pushPose();
        poseStack.translate(attachment.x, attachment.y + NAMETAG_LIFT, attachment.z);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(0.025F, -0.025F, 0.025F);
        int dim = 0x80;
        if (seeThrough) {
            quads(collector, poseStack, RenderTypes.textBackground(), left, split, left + width, top, height,
                    fill, track, 0xFF, LightTexture.lightCoordsWithEmission(state.lightCoords, 2));
            quads(collector, poseStack, RenderTypes.textBackgroundSeeThrough(), left, split, left + width, top, height,
                    fill, track, dim, state.lightCoords);
        } else {
            quads(collector, poseStack, RenderTypes.textBackground(), left, split, left + width, top, height,
                    fill, track, dim, state.lightCoords);
        }
        poseStack.popPose();
    }

    private static void quads(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, float left, float split,
                              float right, float top, float height, int fill, int track, int alpha, int light) {
        int fillColor = withAlpha(fill, alpha);
        int trackColor = withAlpha(track, Math.round(((track >>> 24) & 0xFF) * alpha / 255f));
        float bottom = top + height;
        collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
            if (split > left) {
                quad(consumer, pose, left, top, split, bottom, fillColor, light);
            }
            if (right > split) {
                quad(consumer, pose, split, top, right, bottom, trackColor, light);
            }
        });
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float x1, float y1, int color, int light) {
        consumer.addVertex(pose, x0, y0, 0.0F).setColor(color).setLight(light);
        consumer.addVertex(pose, x0, y1, 0.0F).setColor(color).setLight(light);
        consumer.addVertex(pose, x1, y1, 0.0F).setColor(color).setLight(light);
        consumer.addVertex(pose, x1, y0, 0.0F).setColor(color).setLight(light);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    /** Friend coloring of a nametag Component. */
    static final class FriendStyle {
        private FriendStyle() {
        }

        /**
         * Colors the part of the nametag that spells the nick (team prefixes and suffixes keep their colors). When the
         * nick is not found as plain text (per-letter gradients), the marker is added so a friend is still marked.
         */
        static Component recolor(Component nameTag, String nick, int rgb, boolean marker, String markerGlyph) {
            String lowerNick = nick.toLowerCase(Locale.ROOT);
            MutableComponent out = Component.empty();
            boolean[] found = {false};
            Style friend = Style.EMPTY.withColor(rgb);
            nameTag.visit((style, text) -> {
                int at = found[0] || lowerNick.isEmpty() ? -1 : text.toLowerCase(Locale.ROOT).indexOf(lowerNick);
                if (at < 0) {
                    out.append(Component.literal(text).withStyle(style));
                } else {
                    found[0] = true;
                    if (at > 0) {
                        out.append(Component.literal(text.substring(0, at)).withStyle(style));
                    }
                    out.append(Component.literal(text.substring(at, at + nick.length())).withStyle(style.withColor(rgb)));
                    if (at + nick.length() < text.length()) {
                        out.append(Component.literal(text.substring(at + nick.length())).withStyle(style));
                    }
                }
                return Optional.empty();
            }, Style.EMPTY);
            if (marker || !found[0]) {
                return Component.empty().append(Component.literal(markerGlyph + " ").withStyle(friend)).append(out);
            }
            return out;
        }
    }
}
