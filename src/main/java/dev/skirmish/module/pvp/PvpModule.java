package dev.skirmish.module.pvp;

import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.DamageInfo;
import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PvP HUD: the combat-tag (КТ) countdown and the local player's own item cooldowns. On HolyWorld the tag is read
 * from the sidebar board or a boss bar ({@link TagParser}); elsewhere, or while no server format has been seen on
 * this connection, a local timer restarted by PvP hits stands in. Read and render only: nothing is sent, no action
 * is automated, and other players' cooldowns are never shown or estimated.
 */
public final class PvpModule extends Module {
    public static final String ID = "pvp";
    private static final int LOGGED_LINES_MAX = 256;

    final BoolSetting combatTagHud = (BoolSetting) add(new BoolSetting("combat_tag_hud", true)).feature("combat_tag_hud");
    final BoolSetting serverTimer = add(new BoolSetting("server_timer", true));
    final NumberSetting tagDuration = add(new NumberSetting("tag_duration", 20, 5, 60, 1).unit(" s"));
    final BoolSetting cooldownHud = (BoolSetting) add(new BoolSetting("cooldown_hud", true)).feature("cooldown_hud");

    private final TagClock clock = new TagClock();
    private TagParser.@Nullable Reading reading;
    /** A server tag was parsed on this connection: from then on the board is trusted over the local timer. */
    private boolean serverFormatSeen;
    private boolean boardDumped;
    private final Set<String> loggedLines = new LinkedHashSet<>();

    /**
     * What the combat-tag element shows.
     *
     * @param seconds   whole seconds left, -1 when the server gives only a bar
     * @param fraction  ring fill in [0, 1]
     * @param opponents tagged opponents from the board
     */
    record TagView(int seconds, float fraction, List<String> opponents, Source source) {
    }

    enum Source {
        BOARD, BOSS_BAR, LOCAL
    }

    public PvpModule() {
        super(ID, true);
    }

    @Override
    public void onInitialize() {
        Hud.get().register(new CombatTagHud(this));
        Hud.get().register(new CooldownHud(this));
        CombatTracker.get().addListener(new CombatListener() {
            @Override
            public void onDamage(DamageInfo info) {
                if (isEnabled() && isPvpHit(info)) {
                    clock.hit(info.timeMs());
                }
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetConnection());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetConnection());
    }

    /** A player hit me, or I hit a player. */
    private static boolean isPvpHit(DamageInfo info) {
        Combatant attacker = info.attacker();
        if (attacker == null) {
            return false;
        }
        if (info.onMe()) {
            return attacker.player() && !attacker.self();
        }
        return info.byMe() && info.victim().player() && !info.victim().self();
    }

    private void resetConnection() {
        reading = null;
        serverFormatSeen = false;
        boardDumped = false;
        clock.clearServer();
        clock.clearLocal();
    }

    @Override
    protected void onDisable() {
        resetConnection();
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !combatTagHud.get() || !serverTimer.get() || !HolyWorld.isConnected()) {
            reading = null;
            clock.clearServer();
            return;
        }
        long now = System.currentTimeMillis();
        ServerSurfaces.Board board = ServerSurfaces.board(mc);
        List<TagParser.BossBar> bars = ServerSurfaces.bossBars(mc);
        TagParser.Result result = TagParser.parse(board.title(), board.lines(), bars, mc.player.getGameProfile().name());
        TagParser.Reading next = result.reading();
        if (isDebug()) {
            result.unrecognized().forEach(line -> logOnce("unrecognized candidate: " + line));
            logChange(next);
            dumpIfMissed(board, bars, next, now);
        }
        reading = next;
        if (next == null) {
            clock.clearServer();
            return;
        }
        serverFormatSeen = true;
        if (next.seconds() >= 0) {
            clock.serverSeconds(next.seconds(), now);
        } else {
            clock.clearServer();
        }
    }

    /** The tag to show now, or null when not tagged (or the tag reached 0). */
    @Nullable TagView tag(long nowMs) {
        TagParser.Reading r = reading;
        if (r != null) {
            Source source = r.source() == TagParser.Source.BOARD ? Source.BOARD : Source.BOSS_BAR;
            if (r.seconds() == 0) {
                return null;
            }
            if (r.seconds() > 0) {
                return new TagView(r.seconds(), clock.serverFraction(nowMs), r.opponents(), source);
            }
            return new TagView(-1, Float.isNaN(r.progress()) ? 1f : r.progress(), r.opponents(), source);
        }
        if (serverFormatSeen) {
            return null;
        }
        long duration = Math.round(tagDuration.get() * 1000);
        float remaining = clock.localRemaining(nowMs, duration);
        if (remaining <= 0f) {
            return null;
        }
        return new TagView((int) Math.ceil(remaining), remaining * 1000f / duration, List.of(), Source.LOCAL);
    }

    // ---- debug capture: helps pin down HolyWorld's real formats ----

    private void logOnce(String line) {
        if (loggedLines.add(line)) {
            log(line);
            if (loggedLines.size() > LOGGED_LINES_MAX) {
                loggedLines.remove(loggedLines.iterator().next());
            }
        }
    }

    private void logChange(TagParser.@Nullable Reading next) {
        TagParser.Reading prev = reading;
        boolean changed = prev == null ? next != null : next == null || prev.source() != next.source()
                || !prev.opponents().equals(next.opponents());
        if (changed) {
            log(next == null ? "server tag ended" : "server tag from " + next.source() + ": \"" + next.line()
                    + "\" seconds=" + next.seconds() + " opponents=" + next.opponents());
        }
    }

    /** A local PvP hit happened on HolyWorld but nothing was parsed: dump the board and boss bars once per tag. */
    private void dumpIfMissed(ServerSurfaces.Board board, List<TagParser.BossBar> bars, TagParser.@Nullable Reading next, long now) {
        boolean localTag = clock.localRemaining(now, Math.round(tagDuration.get() * 1000)) > 0f;
        if (!localTag) {
            boardDumped = false;
            return;
        }
        if (next != null || boardDumped) {
            return;
        }
        boardDumped = true;
        StringBuilder dump = new StringBuilder("PvP hit without a parsed tag; board title=\"").append(board.title()).append('"');
        for (int i = 0; i < board.lines().size(); i++) {
            dump.append("\n  line ").append(i).append(": \"").append(board.lines().get(i)).append("\" score=").append(board.scores().get(i));
        }
        for (TagParser.BossBar bar : bars) {
            dump.append("\n  bossbar: \"").append(bar.name()).append("\" progress=").append(bar.progress());
        }
        log(dump.toString());
    }
}
