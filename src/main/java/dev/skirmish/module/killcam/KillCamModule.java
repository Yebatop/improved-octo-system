package dev.skirmish.module.killcam;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.DamageInfo;
import dev.skirmish.combat.Fight;
import dev.skirmish.combat.OwnDeath;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** KillCam: records the last seconds around me and replays them from the death screen. */
public final class KillCamModule extends Module {
    public static final String ID = "killcam";

    private final NumberSetting recordSeconds = add(new NumberSetting("record_seconds", 10, 4, Recorder.MAX_PRE_DEATH_TICKS / 20.0, 1).unit(" s"));
    private final EnumSetting<CameraMode> defaultCamera = add(new EnumSetting<>("default_camera", CameraMode.KILLER))
            .valueFeature(CameraMode.FREE, "freecam");
    private final BoolSetting deathButton = add(new BoolSetting("death_button", true));
    private final KeySetting replayKey = add(new KeySetting("replay_key", "key.skirmish.killcam.replay"));
    final BoolSetting hudIndicator = add(new BoolSetting("hud_indicator", true));
    private final NumberSetting radius = add(new NumberSetting("radius", 32, 8, 64, 1).unit(" m"));
    private final NumberSetting afterDeath = add(new NumberSetting("after_death", 1.0, 0, Recorder.MAX_TAIL_TICKS / 20.0, 0.5).unit(" s"));
    private final EnumSetting<ReplaySpeed> defaultSpeed = add(new EnumSetting<>("default_speed", ReplaySpeed.X1));
    private final BoolSetting loop = add(new BoolSetting("loop", false));
    private final BoolSetting hideDrops = add(new BoolSetting("hide_drops", true));
    private final BoolSetting particles = add(new BoolSetting("particles", true));
    private final BoolSetting sounds = add(new BoolSetting("sounds", true));

    private final Recorder recorder = new Recorder(this);

    public KillCamModule() {
        super(ID, true);
    }

    int preDeathTicks() {
        return (int) Math.round(recordSeconds.get() * 20);
    }

    double radius() {
        return radius.get();
    }

    int tailTicks() {
        return (int) Math.round(afterDeath.get() * 20);
    }

    /** Free camera is a Feature Control id ("freecam"): hidden and unusable while blocked. */
    boolean cameraAllowed(CameraMode mode) {
        return !defaultCamera.isValueBlocked(mode);
    }

    CameraMode defaultCamera() {
        return defaultCamera.get();
    }

    double defaultSpeed() {
        return defaultSpeed.get().value;
    }

    boolean loop() {
        return loop.get();
    }

    boolean hideDrops() {
        return hideDrops.get();
    }

    boolean particles() {
        return particles.get();
    }

    boolean sounds() {
        return sounds.get();
    }

    @Override
    public void onInitialize() {
        CombatTracker.get().addListener(new Listener());
        dev.skirmish.hud.Hud.get().register(new KillCamIndicator(this, recorder));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof DeathScreen && isEnabled()) {
                decorateDeathScreen(client, screen, width, height);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            ReplaySession session = ReplaySession.current();
            if (session != null) {
                session.stop("disconnected", ReplaySession.SCREEN_KEEP);
            }
            recorder.reset("disconnected", false);
        }));
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> {
            ReplaySession session = ReplaySession.current();
            if (session != null) {
                session.stop("world changed to " + level.dimension().identifier(), ReplaySession.SCREEN_KEEP);
            }
        });
    }

    @Override
    protected void onDisable() {
        ReplaySession session = ReplaySession.current();
        if (session != null) {
            session.stop("module disabled", ReplaySession.SCREEN_DEATH);
        }
        recorder.reset("module disabled", true);
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        ReplaySession session = ReplaySession.current();
        if (session != null) {
            session.tick();
        }
        recorder.tick(mc);
        while (SkirmishKeys.KILLCAM_REPLAY.consumeClick()) {
            if (mc.player != null && mc.player.isDeadOrDying()) {
                ReplaySession.start(this, recorder, "key without screen");
            }
        }
    }

    private void decorateDeathScreen(Minecraft client, Screen screen, int width, int height) {
        LocalPlayer player = client.player;
        if (!deathButton.get()) {
            log("death screen: Watch button disabled in settings (the key still works)");
        } else {
            dev.skirmish.ui.widget.Button watch = new dev.skirmish.ui.widget.Button(
                    () -> dev.skirmish.ui.Ui.tr("skirmish.killcam.watch"), true, () -> ReplaySession.start(this, recorder, "button"));
            dev.skirmish.ui.widget.ScreenWidgets.attach(screen, (ui, widgets, mx, my) -> {
                boolean available = client.player != null && recorder.hasReplay(client.player) && !ReplaySession.isActive();
                String l = "layout.death_button.";
                float w = ui.num(l + "width");
                float h = ui.num(l + "height");
                // Below vanilla's two buttons (height / 4 + 72 and + 96, 20 GUI px each).
                float y = (float) dev.skirmish.ui.Ui.toDesignOnVanilla(screen.height / 4 + 96 + 20) + ui.num(l + "gap");
                watch.enabled = available;
                watch.bounds((ui.width() - w) / 2f, y, w, h);
                widgets.widget(ui, watch, mx, my);
                String info = watchInfo(available);
                ui.text("death_info", info, (ui.width() - ui.textWidth("death_info", info)) / 2f, y + h + ui.num(l + "info_gap"));
            });
            ReplayBuffer buffer = recorder.buffer();
            log("death screen opened: Watch button added (recorded %.2f s, %d players, dead=%s, frozen=%s)",
                    buffer == null || buffer.isEmpty() ? 0.0 : (buffer.currentTick() - buffer.oldestTick() + 1) / 20.0,
                    buffer == null ? 0 : buffer.assignedTracks(), player != null && player.isDeadOrDying(), recorder.isFrozen());
        }
        ScreenKeyboardEvents.afterKeyPress(screen).register((s, key) -> {
            if (SkirmishKeys.KILLCAM_REPLAY.matches(key)) {
                ReplaySession.start(this, recorder, "key on death screen");
            }
        });
    }

    /** "10,0 с записи · убийца Bob · клавиша K", or why there is nothing to watch. */
    private String watchInfo(boolean available) {
        ReplayBuffer buffer = recorder.buffer();
        if (!available || buffer == null) {
            return dev.skirmish.ui.Ui.tr("skirmish.killcam.watch.unavailable");
        }
        OwnDeath death = recorder.death();
        Combatant killer = death == null ? null : death.killer();
        return dev.skirmish.ui.Ui.tr("skirmish.killcam.watch.info",
                dev.skirmish.ui.Ui.decimal(Math.min(preDeathTicks(), recorder.deathTick() - buffer.oldestTick()) / 20.0, 1),
                killer == null ? "—" : killer.name(), dev.skirmish.ui.KeyNames.shortName(SkirmishKeys.KILLCAM_REPLAY));
    }

    private final class Listener implements CombatListener {
        @Override
        public void onDamage(DamageInfo info) {
            if (!isEnabled()) {
                return;
            }
            Combatant attacker = info.attacker();
            recorder.onEvent(ReplayBuffer.EVENT_HIT, info.victim().uuid(), attacker == null ? null : attacker.uuid(), info.damageType(),
                    (attacker == null ? "?" : attacker.name()) + " -> " + info.victim().name() + " (" + info.damageType() + ")");
        }

        @Override
        public void onCrit(Entity target, boolean magic) {
            if (isEnabled()) {
                recorder.onEvent(magic ? ReplayBuffer.EVENT_MAGIC_CRIT : ReplayBuffer.EVENT_CRIT, target.getUUID(), null, null,
                        (magic ? "magic crit on " : "crit on ") + target.getName().getString());
            }
        }

        @Override
        public void onTotemPop(Combatant entity, @Nullable Fight fight) {
            if (isEnabled()) {
                recorder.onEvent(ReplayBuffer.EVENT_TOTEM, entity.uuid(), null, null, "totem of " + entity.name());
            }
        }

        @Override
        public void onEntityDeath(Combatant entity, @Nullable Fight fight) {
            if (isEnabled() && entity.player()) {
                recorder.onEvent(ReplayBuffer.EVENT_DEATH, entity.uuid(), null, null, "death of " + entity.name());
            }
        }

        @Override
        public void onOwnDeath(OwnDeath death) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (isEnabled() && player != null) {
                recorder.onOwnDeath(death, player);
            }
        }
    }
}
