package dev.skirmish.module.recap;

import dev.skirmish.SkirmishClient;
import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.Fight;
import dev.skirmish.combat.FightEndReason;
import dev.skirmish.combat.OwnDeath;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.analytics.AnalyticsHub;
import dev.skirmish.setting.ActionSetting;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.util.ServerContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * «Session Recap»: totals of a play session (join → disconnect): kills, deaths, K/D, fights won, the best fight (most
 * damage), totems, time played, experience levels and — when the sidebar shows a balance line — coins. The window
 * opens from the module's button, {@code /skirmish recap}, or by itself after a disconnect when «Показывать при выходе»
 * is on (off by default); the last finished session stays in memory. «Сохранить PNG» renders a card the KillCard way.
 * Everything comes from the combat tracker and what the HUD already shows; nothing is sent.
 */
public final class SessionRecapModule extends Module {
    public static final String ID = "session_recap";

    final BoolSetting autoOpen = add(new BoolSetting("auto_open", false));
    final BoolSetting coinsFromBoard = add(new BoolSetting("coins_from_board", true));
    final ActionSetting show = add(new ActionSetting("show", () -> open(Minecraft.getInstance().screen)));
    final ActionSetting openFolder = add(new ActionSetting("open_folder", this::openFolder));

    private final RecapTracker tracker = new RecapTracker();
    private @Nullable SessionRecap last;
    private int tickCounter;
    private @Nullable ExecutorService executor;

    public SessionRecapModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.INTERFACE;
    }

    @Override
    public void onInitialize() {
        AnalyticsHub.get().require(this::isEnabled);
        CombatTracker.get().addListener(new Listener());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(this::onJoin));
        // The combat tracker's own DISCONNECT handler ends the open fights first (registered earlier).
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::onDisconnect));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                // Merged into the core "/skirmish" node by Brigadier.
                dispatcher.register(literal("skirmish").then(literal("recap").executes(ctx -> {
                    SkirmishClient.openScreenNextTick(() -> screen(null));
                    return 1;
                }))));
    }

    private void onJoin() {
        if (tracker.active()) {
            // A proxy transfer between sub-servers: the same session goes on.
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String player = mc.player != null ? mc.player.getGameProfile().name() : mc.getUser().getName();
        tracker.begin(System.currentTimeMillis(), player, ServerContext.serverDisplayName());
        log("session started on %s as %s", ServerContext.serverDisplayName(), player);
    }

    private void onDisconnect() {
        SessionRecap recap = tracker.end(System.currentTimeMillis());
        if (recap == null) {
            return;
        }
        last = recap;
        log("session ended: %d kills, %d deaths, %d fights, %d ms, coins %s, levels %s", recap.kills(), recap.deaths(),
                recap.fights(), recap.playtimeMs(), recap.coins(), recap.levels());
        if (isEnabled() && autoOpen.get()) {
            SkirmishClient.openScreenNextTick(() -> new RecapScreen(this, recap, Minecraft.getInstance().screen));
        }
    }

    @Override
    public void tick() {
        AnalyticsHub.get().tick();
        Minecraft mc = Minecraft.getInstance();
        if (!tracker.active() || mc.player == null || ++tickCounter % 20 != 0) {
            return;
        }
        tracker.level(mc.player.experienceLevel);
        if (coinsFromBoard.get()) {
            OptionalLong balance = BalanceLine.find(sidebar(mc));
            if (balance.isPresent()) {
                tracker.balance(balance.getAsLong());
            }
        }
    }

    /** The recap to show now: the running session while connected, else the last finished one. */
    @Nullable SessionRecap current() {
        return tracker.active() ? tracker.snapshot(System.currentTimeMillis(), false) : last;
    }

    RecapScreen screen(net.minecraft.client.gui.screens.@Nullable Screen parent) {
        return new RecapScreen(this, current(), parent);
    }

    private void open(net.minecraft.client.gui.screens.@Nullable Screen parent) {
        Minecraft.getInstance().setScreen(screen(parent));
    }

    static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("recaps");
    }

    void openFolder() {
        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            error("cannot create " + dir, e);
            return;
        }
        Util.getPlatform().openPath(dir);
    }

    synchronized ExecutorService executor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Skirmish Recap");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY + 1);
                return thread;
            });
        }
        return executor;
    }

    /** Sidebar lines as the vanilla HUD shows them (same objective choice and order). Read only. */
    private static List<String> sidebar(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return List.of();
        }
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = null;
        PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
        if (team != null) {
            DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
            if (slot != null) {
                objective = scoreboard.getDisplayObjective(slot);
            }
        }
        if (objective == null) {
            objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        }
        if (objective == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
            if (!entry.isHidden()) {
                lines.add(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()).getString());
            }
        }
        return lines;
    }

    static RecapFight summary(Fight fight) {
        FightEndReason reason = fight.endReason();
        RecapFight.Result result = reason == FightEndReason.KILL ? RecapFight.Result.WIN
                : reason == FightEndReason.OWN_DEATH ? RecapFight.Result.LOSS : RecapFight.Result.OTHER;
        long end = fight.endMs() >= 0 ? fight.endMs() : System.currentTimeMillis();
        return new RecapFight(fight.opponent().name(), fight.opponent().uuid(), fight.damageDealt(), fight.isDamageKnown(),
                fight.hitsDealt(), fight.hitsTaken(), fight.opponentTotems(), fight.durationMs(end), result);
    }

    private final class Listener implements CombatListener {
        @Override
        public void onKill(Fight fight) {
            if (isEnabled()) {
                tracker.kill();
            }
        }

        @Override
        public void onOwnDeath(OwnDeath death) {
            if (isEnabled()) {
                tracker.death();
            }
        }

        @Override
        public void onTotemPop(Combatant entity, @Nullable Fight fight) {
            if (isEnabled() && entity.self()) {
                tracker.myTotem();
            }
        }

        @Override
        public void onFightEnd(Fight fight) {
            if (isEnabled()) {
                // A fight against someone invisible the whole time is counted but never named (HolyWorld rule).
                tracker.fightEnded(summary(fight), AnalyticsHub.get().seenVisible(fight));
            }
        }
    }
}
