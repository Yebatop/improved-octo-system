package dev.skirmish.module.tnttimer;

import dev.skirmish.SkirmishClient;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.hwtimers.JsonTables;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * «TNT Timer»: seconds left on every primed TNT in plain view, plus its HolyWorld type when the server makes it
 * recognisable (hidden custom name, block state or a non-vanilla fuse; see {@link TntTable}). Drawn through the
 * entity's own render pass as depth-tested world text, and only while a ray from the camera reaches the TNT, so
 * nothing shows through walls. The optional blast ring ({@code tnt_timer_radius}) is off by default and follows the
 * same line-of-sight rule. Read and render only.
 */
public final class TntTimerModule extends Module {
    public static final String ID = "tnt_timer";
    public static final String RADIUS_FEATURE = "tnt_timer_radius";
    private static final int PRUNE_TICKS = 40;

    private static @Nullable TntTimerModule instance;

    final BoolSetting decimals = add(new BoolSetting("decimals", true));
    final BoolSetting showType = add(new BoolSetting("show_type", true));
    final NumberSetting maxDistance = add(new NumberSetting("max_distance", 48, 8, 96, 4).unit(" m"));
    final BoolSetting radiusRing = (BoolSetting) add(new BoolSetting("radius_ring", false)).feature(RADIUS_FEATURE);

    private TntTable table = TntTable.bundled();
    /** Highest fuse seen per entity id: the starting fuse, which tells the Prime long TNT apart. */
    private final Map<Integer, Integer> startFuse = new HashMap<>();
    private final Set<String> loggedKinds = new HashSet<>();
    private int ticks;

    /** What the renderer draws for one TNT this frame. */
    public record Label(Component text, TntTable.@Nullable Ring ring, int ringColor) {
    }

    public TntTimerModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }

    public static @Nullable TntTimerModule instance() {
        return instance;
    }

    @Override
    public void onInitialize() {
        instance = this;
        JsonTables.Loaded loaded = JsonTables.load(SkirmishClient.configDir(), TntTable.OVERRIDE_NAME, TntTable.RESOURCE);
        table = TntTable.parse(loaded.root());
        if (loaded.problem() != null) {
            error("TNT table override ignored: " + loaded.problem(), null);
        }
        table.problems().forEach(p -> error("TNT table: " + p, null));
    }

    @Override
    protected void onDisable() {
        startFuse.clear();
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks % PRUNE_TICKS != 0 || mc.level == null) {
            return;
        }
        startFuse.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof PrimedTnt));
    }

    /**
     * Called while the TNT's render state is extracted (render thread, once per frame): the label to draw, or null
     * when the module is off, the TNT is too far or out of plain view.
     */
    public @Nullable Label label(PrimedTnt tnt, float partialTick) {
        if (!isEnabled()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().position();
        Vec3 center = tnt.getBoundingBox().getCenter();
        double max = maxDistance.get();
        if (eye.distanceToSqr(center) > max * max || !inPlainView(mc, eye, tnt)) {
            return null;
        }
        int fuse = tnt.getFuse();
        int start = startFuse.merge(tnt.getId(), fuse, Math::max);
        String name = tnt.getCustomName() == null ? "" : tnt.getCustomName().getString();
        String blockId = BuiltInRegistries.BLOCK.getKey(tnt.getBlockState().getBlock()).toString();
        TntTable.TntType type = table.identify(TntTable.normalizeName(name), blockId, start);
        logKind(name, blockId, start, type);

        Theme theme = Theme.get();
        float seconds = Math.max(0f, (fuse - partialTick) / 20f);
        int color = theme.color(seconds <= 1f ? "bad" : seconds <= 2f ? "warn" : "good") & 0xFFFFFF;
        String time = decimals.get() ? Ui.decimal(seconds, 1) : Integer.toString((int) Math.ceil(seconds));
        MutableComponent text = Component.literal(Ui.tr("skirmish.tnttimer.seconds", time)).withStyle(Style.EMPTY.withColor(color));
        if (showType.get() && type != null) {
            text.append(Component.literal(" · " + typeName(type)).withStyle(Style.EMPTY.withColor(theme.color("text_2") & 0xFFFFFF)));
        }
        TntTable.Ring ring = radiusRing.get() ? table.ring(type) : null;
        return new Label(text, ring, theme.color("tnt_ring"));
    }

    /** A ray from the camera reaches the TNT's centre or top without hitting a block that blocks sight. */
    private static boolean inPlainView(Minecraft mc, Vec3 eye, PrimedTnt tnt) {
        Vec3 center = tnt.getBoundingBox().getCenter();
        Vec3 top = new Vec3(center.x, tnt.getBoundingBox().maxY - 0.05, center.z);
        for (Vec3 target : new Vec3[]{center, top}) {
            HitResult hit = mc.level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(target) < 0.01) {
                return true;
            }
        }
        return false;
    }

    static String typeName(TntTable.TntType type) {
        String key = "skirmish.tnttimer.type." + type.id();
        String text = Ui.tr(key);
        return text.equals(key) ? type.id() : text;
    }

    /** Debug capture: how the server marks each kind of primed TNT (name, block, starting fuse), once per kind. */
    private void logKind(String name, String blockId, int start, TntTable.@Nullable TntType type) {
        if (!isDebug()) {
            return;
        }
        String key = name + "|" + blockId + "|" + (start > table.vanillaFuseTicks() + 2 ? start : 0);
        if (loggedKinds.add(key) && loggedKinds.size() < 512) {
            log("primed TNT: name=\"%s\" block=%s start fuse=%d → %s", name, blockId, start, type == null ? "plain/unknown" : type.id());
        }
    }
}
