package dev.skirmish.module.elytra;

import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * «Elytra HUD»: while you glide, a panel with your speed, height and angle, rockets left, how long the elytra still
 * flies (1 durability per second) and the distance and time to the selected waypoint. Read-only; Feature Control
 * id {@code elytra_hud}.
 */
public final class ElytraHudModule extends Module {
    public static final String ID = "elytra_hud";
    static final String L = "layout.elytra.";

    final BoolSetting waypoint = add(new BoolSetting("waypoint", true));
    final BoolSetting angle = add(new BoolSetting("angle", true));

    private @Nullable Vec3 lastPos;
    private double speed = -1;
    private double horizontal = -1;
    private long flyingAt = -1;

    public ElytraHudModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.UTILITY;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        Hud.get().register(new ElytraHud(this));
    }

    @Override
    public void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isFallFlying()) {
            lastPos = null;
            return;
        }
        Vec3 pos = player.position();
        if (lastPos != null) {
            Vec3 d = pos.subtract(lastPos);
            double factor = Theme.get().num(L + "smoothing");
            speed = FlightMath.smooth(speed, d.length() * 20.0, factor);
            horizontal = FlightMath.smooth(horizontal, Math.sqrt(d.x * d.x + d.z * d.z) * 20.0, factor);
        }
        if (flyingAt < 0 || Util.getMillis() - flyingAt > 2_000) {
            log("gliding");
        }
        lastPos = pos;
        flyingAt = Util.getMillis();
    }

    @Override
    protected void onDisable() {
        lastPos = null;
        speed = -1;
        horizontal = -1;
    }

    /** Gliding now or within the last {@code linger_ms}. */
    boolean flying() {
        return flyingAt >= 0 && Util.getMillis() - flyingAt < Theme.get().num(L + "linger_ms");
    }

    double speed() {
        return Math.max(0, speed);
    }

    double horizontalSpeed() {
        return Math.max(0, horizontal);
    }
}
