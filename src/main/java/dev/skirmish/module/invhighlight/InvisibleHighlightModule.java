package dev.skirmish.module.invhighlight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * «Invisible Highlight»: the top of the block an invisible player stands on is outlined and softly filled, pulsing,
 * so you can tell where they are. HolyWorld's rules allow exactly this («подсветка блоков, на которых находится
 * игрок под невидимостью»). Only players invisible to you, within range; depth-tested like block outlines, so a
 * highlight behind a wall is hidden by the wall. While a player jumps, their last block stays lit for a moment.
 * Render-only; Feature Control id {@code invisible_highlight}.
 */
public final class InvisibleHighlightModule extends Module {
    public static final String ID = "invisible_highlight";
    private static final String L = "layout.invhighlight.";

    /** Values match the {@code ih_<name>} colours of theme/invhighlight.json. */
    public enum Color {
        VIOLET, RED, AQUA, GOLD, WHITE
    }

    final EnumSetting<Color> color = add(new EnumSetting<>("color", Color.VIOLET));
    final BoolSetting fill = add(new BoolSetting("fill", true));
    final BoolSetting pulse = add(new BoolSetting("pulse", true));
    final NumberSetting range = add(new NumberSetting("range", 32, 8, 64, 4).unit("m"));

    /** A lit block top: the block and the height of its top face. */
    record Spot(BlockPos pos, double top) {
    }

    private record Seen(Spot spot, long at) {
    }

    private final Map<Integer, Seen> lastSpots = new HashMap<>();
    private List<Spot> spots = List.of();

    public InvisibleHighlightModule() {
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
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (isEnabled() && !spots.isEmpty()) {
                render(context);
            }
        });
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            spots = List.of();
            lastSpots.clear();
            return;
        }
        long now = Util.getMillis();
        long keep = Math.round(Theme.get().num(L + "keep_ms"));
        double max = range.get();
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator() || !player.isInvisibleTo(mc.player)
                    || player.distanceTo(mc.player) > max) {
                continue;
            }
            if (player.onGround()) {
                BlockPos pos = player.getOnPos();
                VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
                double top = shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y);
                lastSpots.put(player.getId(), new Seen(new Spot(pos.immutable(), pos.getY() + top), now));
            }
        }
        lastSpots.entrySet().removeIf(e -> now - e.getValue().at() > keep || !stillInvisible(mc, e.getKey()));
        List<Spot> out = new ArrayList<>();
        for (Seen seen : lastSpots.values()) {
            if (!out.contains(seen.spot())) {
                out.add(seen.spot());
            }
        }
        spots = out;
    }

    private static boolean stillInvisible(Minecraft mc, int id) {
        return mc.level.getEntity(id) instanceof AbstractClientPlayer p && p.isAlive() && p.isInvisibleTo(mc.player);
    }

    @Override
    protected void onDisable() {
        spots = List.of();
        lastSpots.clear();
    }

    /** Pulse alpha factor 0.55..1 over {@code pulse_ms}. */
    static float pulseFactor(long now, float periodMs) {
        double phase = (now % (long) periodMs) / periodMs * Math.PI * 2;
        return (float) (0.775 + 0.225 * Math.sin(phase));
    }

    private void render(WorldRenderContext context) {
        Theme theme = Theme.get();
        Vec3 cam = context.worldState().cameraRenderState.pos;
        PoseStack pose = context.matrices();
        int base = theme.color("ih_" + color.get().name().toLowerCase(Locale.ROOT));
        float a = pulse.get() ? pulseFactor(Util.getMillis(), theme.num(L + "pulse_ms")) : 1f;
        int line = withAlpha(base, Math.round(theme.num(L + "line_alpha") * a));
        int area = withAlpha(base, Math.round(theme.num(L + "fill_alpha") * a));
        float lift = theme.num(L + "lift");
        float inset = theme.num(L + "inset");
        float width = theme.num(L + "line_width");
        for (Spot spot : spots) {
            pose.pushPose();
            pose.translate(spot.pos().getX() - cam.x, spot.top() + lift - cam.y, spot.pos().getZ() - cam.z);
            float x0 = inset;
            float x1 = 1f - inset;
            if (fill.get()) {
                context.commandQueue().submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, consumer) -> {
                    consumer.addVertex(p, x0, 0f, x0).setColor(area);
                    consumer.addVertex(p, x0, 0f, x1).setColor(area);
                    consumer.addVertex(p, x1, 0f, x1).setColor(area);
                    consumer.addVertex(p, x1, 0f, x0).setColor(area);
                });
            }
            context.commandQueue().submitCustomGeometry(pose, RenderTypes.lines(), (p, consumer) -> {
                edge(consumer, p, x0, x0, x1, x0, line, width);
                edge(consumer, p, x1, x0, x1, x1, line, width);
                edge(consumer, p, x1, x1, x0, x1, line, width);
                edge(consumer, p, x0, x1, x0, x0, line, width);
            });
            pose.popPose();
        }
    }

    private static void edge(VertexConsumer consumer, PoseStack.Pose pose, float xa, float za, float xb, float zb, int color, float width) {
        float dx = xb - xa;
        float dz = zb - za;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        consumer.addVertex(pose, xa, 0f, za).setColor(color).setNormal(pose, dx / len, 0f, dz / len).setLineWidth(width);
        consumer.addVertex(pose, xb, 0f, zb).setColor(color).setNormal(pose, dx / len, 0f, dz / len).setLineWidth(width);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }
}
