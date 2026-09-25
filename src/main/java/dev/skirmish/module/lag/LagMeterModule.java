package dev.skirmish.module.lag;

import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * «Лаг-детектор»: server TPS estimated from world time updates, ping from the tab list and the time since the last
 * packet from the server, plus a «сервер не отвечает» banner when nothing arrived for a while (moving during a
 * stall piles up movement the server later rejects, or kicks for). Read-only packet tap; nothing is sent.
 */
public final class LagMeterModule extends Module {
    public static final String ID = "lag_meter";
    private static final long TPS_WINDOW_NANOS = 10_000_000_000L;
    /** Below this the pill counts as «лагает» for the «только при лагах» mode. */
    static final double LAGGING_TPS = 18.0;
    static final long LAGGING_SILENCE_MS = 1000;
    private static final PacketClock CLOCK = new PacketClock();
    private static final TpsEstimator TPS = new TpsEstimator(TPS_WINDOW_NANOS);
    private static @Nullable LagMeterModule instance;

    enum Show {
        ALWAYS, LAGGING
    }

    final BoolSetting hud = add(new BoolSetting("hud", true));
    final EnumSetting<Show> show = (EnumSetting<Show>) add(new EnumSetting<>("show", Show.ALWAYS)).under(hud).visibleWhen(hud::get);
    final BoolSetting warning = add(new BoolSetting("warning", true));
    final NumberSetting warnAfter = (NumberSetting) add(new NumberSetting("warn_after", 1.5, 0.5, 10, 0.5).unit(" s"))
            .under(warning).visibleWhen(warning::get);
    final BoolSetting sound = (BoolSetting) add(new BoolSetting("sound", false)).under(warning).visibleWhen(warning::get);

    private boolean wasStalled;

    public LagMeterModule() {
        super(ID, true);
        instance = this;
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
        Hud.get().register(new LagHud(this));
        Hud.get().register(new LagBanner(this));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetConnection());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetConnection());
    }

    private static void resetConnection() {
        CLOCK.reset(System.nanoTime());
        TPS.reset();
    }

    /** Network thread, every packet the client receives. */
    public static void onPacket(Packet<?> packet) {
        long now = System.nanoTime();
        CLOCK.onPacket(now);
        if (packet instanceof ClientboundSetTimePacket time) {
            TPS.onTime(now, time.gameTime());
        }
    }

    @Override
    public void tick() {
        boolean stalled = stalled();
        if (stalled && !wasStalled) {
            log("server silent for " + silenceMs() + " ms, TPS " + tps());
            if (sound.get()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.6f));
            }
        } else if (!stalled && wasStalled) {
            log("server answered again");
        }
        wasStalled = stalled;
    }

    @Override
    protected void onDisable() {
        wasStalled = false;
    }

    /** In a multiplayer (or unpaused singleplayer) world with a live connection. */
    static boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            return false;
        }
        // A paused integrated server sends nothing; that is not lag.
        return !(mc.hasSingleplayerServer() && mc.isPaused());
    }

    long silenceMs() {
        return CLOCK.silenceMs(System.nanoTime());
    }

    boolean hasPackets() {
        return CLOCK.hasPackets();
    }

    double tps() {
        return TPS.tps(System.nanoTime());
    }

    /** Ping the server reports for me in the tab list, or -1. */
    static int ping() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.player == null) {
            return -1;
        }
        PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    boolean stalled() {
        return isEnabled() && warning.get() && inWorld() && hasPackets()
                && PacketClock.stalled(silenceMs(), Math.round(warnAfter.get() * 1000));
    }

    boolean lagging() {
        double tps = tps();
        return (!Double.isNaN(tps) && tps < LAGGING_TPS) || silenceMs() > LAGGING_SILENCE_MS;
    }

    static @Nullable LagMeterModule instance() {
        return instance;
    }
}
