package dev.skirmish.module.regions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.ModuleManager;
import dev.skirmish.module.events.EventsModule;
import dev.skirmish.module.events.ServerParser;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.ui.Theme;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * «Region Bounds»: look at a region block (a private's block with its hologram above) and the area it protects is
 * outlined in the world, with a small pill in the top-centre column: the kind, the size and how far you are from
 * its edge. Sizes come from the HolyWorld wiki (Lite and Prime differ; see {@link RegionTable}). The outline stays
 * for a while after you look away. Read-only: the block under the crosshair (ray up to «Дальность») and hologram
 * entities the server already sent; depth-tested lines like block outlines. Feature Control id {@code region_bounds}.
 */
public final class RegionBoundsModule extends Module {
    public static final String ID = "region_bounds";
    static final String L = "layout.regions.";

    public enum ServerChoice {
        AUTO, LITE, PRIME
    }

    final EnumSetting<ServerChoice> server = add(new EnumSetting<>("server", ServerChoice.AUTO));
    final NumberSetting range = add(new NumberSetting("range", 48, 8, 96, 4).unit("m"));
    final NumberSetting hold = add(new NumberSetting("hold", 20, 3, 120, 1).unit("s"));
    final BoolSetting walls = add(new BoolSetting("walls", true));
    final BoolSetting hologram = add(new BoolSetting("hologram", true));
    final BoolSetting hud = add(new BoolSetting("hud", true));

    /** A region on show: its block, kind, bounds, whether the server was guessed, hologram text, first/last seen. */
    record Shown(BlockPos pos, RegionTable.Type type, int[] box, boolean guessed, String holo, long firstSeen, long seenAt) {
    }

    private final Map<BlockPos, Shown> shown = new LinkedHashMap<>();
    private @Nullable Shown latest;

    public RegionBoundsModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        Hud.get().register(new RegionHud(this));
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (isEnabled() && !shown.isEmpty()) {
                render(context);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    @Override
    protected void onDisable() {
        clear();
    }

    private void clear() {
        shown.clear();
        latest = null;
    }

    /** The region looked at most recently that is still on show. */
    @Nullable Shown latest() {
        return latest;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        long now = Util.getMillis();
        HitResult hit = mc.player.pick(range.get(), 1f, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            look(mc, block.getBlockPos().immutable(), now);
        }
        long keep = Math.round(hold.get() * 1000);
        shown.values().removeIf(s -> now - s.seenAt() > keep || gone(mc.level, s));
        if (latest != null && !shown.containsKey(latest.pos())) {
            latest = shown.values().stream().max(Comparator.comparingLong(Shown::seenAt)).orElse(null);
        }
    }

    private void look(Minecraft mc, BlockPos pos, long now) {
        String id = blockId(mc.level, pos);
        if (!RegionTable.anyServer(id)) {
            return;
        }
        Shown old = shown.get(pos);
        if (old != null) {
            Shown again = new Shown(old.pos(), old.type(), old.box(), old.guessed(), old.holo(), old.firstSeen(), now);
            shown.put(pos, again);
            latest = again;
            return;
        }
        String holo = hologramText(mc.level, pos);
        if (holo == null && hologram.get()) {
            return;
        }
        boolean guessed = false;
        RegionTable.Server srv = switch (server.get()) {
            case LITE -> RegionTable.Server.LITE;
            case PRIME -> RegionTable.Server.PRIME;
            case AUTO -> null;
        };
        if (srv == null) {
            ServerParser.Mode mode = detectedMode();
            RegionTable.Type lite = RegionTable.lookup(RegionTable.Server.LITE, id);
            RegionTable.Type prime = RegionTable.lookup(RegionTable.Server.PRIME, id);
            if (mode == ServerParser.Mode.PRIME || mode == ServerParser.Mode.LITE) {
                srv = mode == ServerParser.Mode.PRIME ? RegionTable.Server.PRIME : RegionTable.Server.LITE;
            } else if (lite == null || prime == null) {
                srv = lite != null ? RegionTable.Server.LITE : RegionTable.Server.PRIME;
            } else {
                srv = RegionTable.Server.LITE;
                guessed = true;
            }
        }
        RegionTable.Type type = RegionTable.lookup(srv, id);
        if (type == null) {
            return;
        }
        int limit = Theme.get().integer(L + "max_regions");
        while (shown.size() >= limit) {
            BlockPos oldest = shown.values().stream().min(Comparator.comparingLong(Shown::seenAt)).map(Shown::pos).orElse(null);
            shown.remove(oldest);
        }
        int[] box = RegionTable.bounds(type, pos.getX(), pos.getY(), pos.getZ());
        Shown s = new Shown(pos, type, box, guessed, holo == null ? "" : holo, now, now);
        shown.put(pos, s);
        latest = s;
        log("region %s (%s%s) at %d %d %d, hologram \"%s\"", type.id(), srv, guessed ? ", server guessed" : "",
                pos.getX(), pos.getY(), pos.getZ(), s.holo());
    }

    /** The region a block of this id makes on the server we are on (the «Сервер» setting or detection), or null. */
    public static RegionTable.@Nullable Type typeFor(String blockId) {
        ServerChoice choice = ModuleManager.get().byId(ID) instanceof RegionBoundsModule m ? m.server.get() : ServerChoice.AUTO;
        RegionTable.Server srv = switch (choice) {
            case LITE -> RegionTable.Server.LITE;
            case PRIME -> RegionTable.Server.PRIME;
            case AUTO -> {
                ServerParser.Mode mode = detectedMode();
                if (mode == ServerParser.Mode.PRIME || mode == ServerParser.Mode.LITE) {
                    yield mode == ServerParser.Mode.PRIME ? RegionTable.Server.PRIME : RegionTable.Server.LITE;
                }
                yield RegionTable.lookup(RegionTable.Server.LITE, blockId) != null ? RegionTable.Server.LITE : RegionTable.Server.PRIME;
            }
        };
        return RegionTable.lookup(srv, blockId);
    }

    private static ServerParser.Mode detectedMode() {
        return ModuleManager.get().byId(EventsModule.ID) instanceof EventsModule events ? events.serverMode()
                : ServerParser.Mode.UNKNOWN;
    }

    private static String blockId(Level level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }

    /** The block was broken or replaced (only checked while its chunk is loaded). */
    private static boolean gone(Level level, Shown s) {
        return level.isLoaded(s.pos()) && !RegionTable.anyServer(blockId(level, s.pos()));
    }

    /**
     * Text of the hologram right above a block: text displays and named armor stands / markers within a few blocks
     * up, top line first. Null when there is none.
     */
    public static @Nullable String hologramText(Level level, BlockPos pos) {
        Theme theme = Theme.get();
        AABB box = new AABB(pos.getX() - 1, pos.getY() + 0.5, pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 1 + theme.num(L + "holo_height"), pos.getZ() + 2);
        List<Entity> lines = new ArrayList<>(level.getEntities((Entity) null, box, RegionBoundsModule::isHologram));
        if (lines.isEmpty()) {
            return null;
        }
        lines.sort(Comparator.comparingDouble((Entity e) -> e.getY()).reversed());
        StringBuilder out = new StringBuilder();
        for (Entity e : lines) {
            String line = e instanceof Display.TextDisplay text ? text.getText().getString()
                    : e.getCustomName() == null ? "" : e.getCustomName().getString();
            if (!line.isBlank()) {
                out.append(out.isEmpty() ? "" : " | ").append(line.strip());
            }
        }
        return out.toString();
    }

    public static boolean isHologram(Entity e) {
        if (e instanceof Display.TextDisplay) {
            return true;
        }
        if (e instanceof ArmorStand stand) {
            return stand.hasCustomName() && (stand.isInvisible() || stand.isMarker());
        }
        return !(e instanceof LivingEntity) && e.hasCustomName() && e.isCustomNameVisible();
    }

    // ---- rendering ----

    /** Fade in over {@code appear_ms} after first seen, fade out over {@code fade_ms} before the hold runs out. */
    static float visibility(long now, long firstSeen, long seenAt, long holdMs, float appearMs, float fadeMs) {
        float in = Math.min(1f, (now - firstSeen) / Math.max(1f, appearMs));
        float left = holdMs - (now - seenAt);
        float out = Math.min(1f, Math.max(0f, left / Math.max(1f, fadeMs)));
        return Math.max(0f, Math.min(in, out));
    }

    private void render(WorldRenderContext context) {
        Theme theme = Theme.get();
        Vec3 cam = context.worldState().cameraRenderState.pos;
        PoseStack pose = context.matrices();
        long now = Util.getMillis();
        long keep = Math.round(hold.get() * 1000);
        float width = theme.num(L + "line_width");
        for (Shown s : List.copyOf(shown.values())) {
            float vis = visibility(now, s.firstSeen(), s.seenAt(), keep, theme.num(L + "appear_ms"), theme.num(L + "fade_ms"));
            if (vis <= 0f) {
                continue;
            }
            int tone = theme.color("rb_" + s.type().id());
            int line = withAlpha(tone, Math.round(theme.num(L + "line_alpha") * vis));
            int wall = withAlpha(tone, Math.round(theme.num(L + "wall_alpha") * vis));
            int[] b = s.box();
            float x0 = (float) (b[0] - cam.x);
            float z0 = (float) (b[2] - cam.z);
            float x1 = (float) (b[3] - cam.x);
            float z1 = (float) (b[5] - cam.z);
            pose.pushPose();
            if (s.type().cube()) {
                float y0 = (float) (b[1] - cam.y);
                float y1 = (float) (b[4] - cam.y);
                if (walls.get()) {
                    context.commandQueue().submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, c) ->
                            sides(c, p, x0, z0, x1, z1, y0, wall, y1, wall));
                }
                context.commandQueue().submitCustomGeometry(pose, RenderTypes.lines(), (p, c) -> {
                    ring(c, p, x0, z0, x1, z1, y0, line, width);
                    ring(c, p, x0, z0, x1, z1, y1, line, width);
                    posts(c, p, x0, z0, x1, z1, y0, line, y1, line, width);
                });
            } else {
                // Height unknown for Lite: a ring at the block's level and walls fading out above and below it.
                float base = (float) (s.pos().getY() + theme.num(L + "lift") - cam.y);
                float top = base + theme.num(L + "column_above");
                float bottom = base - theme.num(L + "column_below");
                int clearLine = withAlpha(tone, 0);
                if (walls.get()) {
                    context.commandQueue().submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, c) -> {
                        sides(c, p, x0, z0, x1, z1, base, wall, top, clearLine);
                        sides(c, p, x0, z0, x1, z1, bottom, clearLine, base, wall);
                    });
                }
                context.commandQueue().submitCustomGeometry(pose, RenderTypes.lines(), (p, c) -> {
                    ring(c, p, x0, z0, x1, z1, base, line, width);
                    posts(c, p, x0, z0, x1, z1, base, line, top, clearLine, width);
                    posts(c, p, x0, z0, x1, z1, bottom, clearLine, base, line, width);
                });
            }
            pose.popPose();
        }
    }

    /** The four side walls between heights ya and yb, with a colour per height (a vertical gradient). */
    private static void sides(VertexConsumer c, PoseStack.Pose p, float x0, float z0, float x1, float z1,
                              float ya, int ca, float yb, int cb) {
        quad(c, p, x0, z0, x1, z0, ya, ca, yb, cb);
        quad(c, p, x1, z0, x1, z1, ya, ca, yb, cb);
        quad(c, p, x1, z1, x0, z1, ya, ca, yb, cb);
        quad(c, p, x0, z1, x0, z0, ya, ca, yb, cb);
    }

    private static void quad(VertexConsumer c, PoseStack.Pose p, float xa, float za, float xb, float zb,
                             float ya, int ca, float yb, int cb) {
        c.addVertex(p, xa, ya, za).setColor(ca);
        c.addVertex(p, xb, ya, zb).setColor(ca);
        c.addVertex(p, xb, yb, zb).setColor(cb);
        c.addVertex(p, xa, yb, za).setColor(cb);
    }

    private static void ring(VertexConsumer c, PoseStack.Pose p, float x0, float z0, float x1, float z1, float y,
                             int color, float width) {
        seg(c, p, x0, y, z0, color, x1, y, z0, color, width);
        seg(c, p, x1, y, z0, color, x1, y, z1, color, width);
        seg(c, p, x1, y, z1, color, x0, y, z1, color, width);
        seg(c, p, x0, y, z1, color, x0, y, z0, color, width);
    }

    /** The four vertical corner edges from ya to yb. */
    private static void posts(VertexConsumer c, PoseStack.Pose p, float x0, float z0, float x1, float z1,
                              float ya, int ca, float yb, int cb, float width) {
        seg(c, p, x0, ya, z0, ca, x0, yb, z0, cb, width);
        seg(c, p, x1, ya, z0, ca, x1, yb, z0, cb, width);
        seg(c, p, x1, ya, z1, ca, x1, yb, z1, cb, width);
        seg(c, p, x0, ya, z1, ca, x0, yb, z1, cb, width);
    }

    private static void seg(VertexConsumer c, PoseStack.Pose p, float xa, float ya, float za, int ca,
                            float xb, float yb, float zb, int cb, float width) {
        float dx = xb - xa;
        float dy = yb - ya;
        float dz = zb - za;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) {
            return;
        }
        c.addVertex(p, xa, ya, za).setColor(ca).setNormal(p, dx / len, dy / len, dz / len).setLineWidth(width);
        c.addVertex(p, xb, yb, zb).setColor(cb).setNormal(p, dx / len, dy / len, dz / len).setLineWidth(width);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }
}
