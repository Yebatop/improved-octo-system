package dev.skirmish.module.analytics.review;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.analytics.AnalyticsHub;
import dev.skirmish.setting.ActionSetting;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.ui.KeyNames;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.ScreenWidgets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * «Разбор боя»: after a fight (kill, my death, timeout) a short hint names the key that opens the review of it; the
 * review window lists the last {@value FightRecorder#KEPT} fights and shows the timeline, hits, crits, combos,
 * totems, reach of my hits, both health curves and, after my death, who killed me and how. Built only from events
 * the client already receives (see {@link FightRecorder}); nothing is sent and nothing is automated.
 */
public final class FightReviewModule extends Module {
    public static final String ID = "fight_review";
    /** A fight with fewer hits both ways gets no hint unless it ended with a kill or a death. */
    static final int HINT_MIN_HITS = 3;

    final BoolSetting hint = add(new BoolSetting("hint", true));
    final NumberSetting hintSeconds = add(new NumberSetting("hint_seconds", 8, 3, 30, 1).unit(" s"));
    final BoolSetting deathButton = add(new BoolSetting("death_button", true));
    final KeySetting key = add(new KeySetting("key", "key.skirmish.fight_review.open"));
    final ActionSetting openList = add(new ActionSetting("open", () -> open(null, Minecraft.getInstance().screen)));

    private final FightRecorder recorder = new FightRecorder(this, this::onEntry);
    private @Nullable ReviewEntry hintEntry;
    /** When the hint became visible (no screen open and alive), -1 while waiting for that. */
    private long hintSince = -1;

    public FightReviewModule() {
        super(ID, true);
        hintSeconds.under(hint).visibleWhen(hint::get);
    }

    @Override
    public Category category() {
        return Category.COMBAT;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        AnalyticsHub.get().require(this::isEnabled);
        CombatTracker.get().addListener(recorder);
        Hud.get().register(new ReviewHint(this));
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> recorder.resetIds());
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof DeathScreen && isEnabled()) {
                decorateDeathScreen(screen);
            }
        });
    }

    @Override
    protected void onDisable() {
        hintEntry = null;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        AnalyticsHub.get().tick();
        recorder.tick(mc);
        while (SkirmishKeys.FIGHT_REVIEW.consumeClick()) {
            if (mc.screen == null) {
                open(null, null);
            }
        }
        if (hintEntry != null && hintSince < 0 && mc.screen == null && mc.player != null && mc.player.isAlive()) {
            hintSince = System.currentTimeMillis();
        }
    }

    private void onEntry(ReviewEntry entry) {
        log("review: %s vs %s, %s", entry.outcome(), entry.opponentName(), entry.fight() == null ? "death outside a fight" : entry.fight());
        if (!entry.significant(HINT_MIN_HITS)) {
            return;
        }
        // One death can end several fights: the hint points at the killer's fight.
        ReviewEntry current = hintEntry;
        if (current != null && current.death() != null && current.death() == entry.death() && current.killedByOpponent()) {
            return;
        }
        hintEntry = entry;
        hintSince = -1;
    }

    /** Recorded fights and deaths, newest first. */
    public List<ReviewEntry> entries() {
        return recorder.entries();
    }

    /** The entry the hint points at while it is on screen. */
    @Nullable ReviewEntry hintEntry(long nowMs) {
        if (!hint.get() || hintEntry == null || hintSince < 0) {
            return null;
        }
        return nowMs - hintSince < Math.round(hintSeconds.get() * 1000) ? hintEntry : null;
    }

    /** Opens the review of {@code entry} (the latest when null) on top of {@code parent}. */
    void open(@Nullable ReviewEntry entry, @Nullable Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        List<ReviewEntry> all = entries();
        ReviewEntry selected = entry != null ? entry : all.isEmpty() ? null : all.getFirst();
        hintEntry = null;
        mc.setScreen(new FightReviewScreen(this, selected, parent));
    }

    /** Key and button on the death screen (keys do not reach the game while a screen is open). */
    private void decorateDeathScreen(Screen screen) {
        ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
            if (SkirmishKeys.FIGHT_REVIEW.matches(event)) {
                openLatestDeath(s);
            }
        });
        if (!deathButton.get()) {
            return;
        }
        Button button = new Button(() -> SkirmishKeys.FIGHT_REVIEW.isUnbound() ? Ui.tr("skirmish.analytics.death.button")
                : Ui.tr("skirmish.analytics.death.button_key", KeyNames.shortName(SkirmishKeys.FIGHT_REVIEW)), false,
                () -> openLatestDeath(screen)).layout("layout.analytics.death.");
        ScreenWidgets.attach(screen, (ui, widgets, mx, my) -> {
            if (latestDeath() == null) {
                return;
            }
            float w = button.preferredWidth(ui);
            float h = button.preferredHeight(ui);
            button.bounds((ui.width() - w) / 2f, ui.height() - ui.num("layout.analytics.death.bottom") - h, w, h);
            widgets.widget(ui, button, mx, my);
        });
        log("death screen: review button added");
    }

    private @Nullable ReviewEntry latestDeath() {
        for (ReviewEntry e : entries()) {
            if (e.death() != null) {
                return System.currentTimeMillis() - e.timeMs() < 120_000 ? e : null;
            }
        }
        return null;
    }

    private void openLatestDeath(Screen parent) {
        ReviewEntry death = latestDeath();
        if (death != null) {
            // The killer's fight when there are several with the same death.
            for (ReviewEntry e : entries()) {
                if (e.death() == death.death() && e.killedByOpponent()) {
                    death = e;
                    break;
                }
            }
            open(death, parent);
        }
    }
}
