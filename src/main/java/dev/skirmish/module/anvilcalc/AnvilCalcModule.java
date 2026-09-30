package dev.skirmish.module.anvilcalc;

import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.module.Module;
import dev.skirmish.module.anvilcalc.calc.RulesProfile;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AnvilCalc: in the anvil screen, a «Calculate» button (or the ANVILCALC_CALCULATE key) plans the cheapest order of
 * merging the enchanted books from the inventory into the item in the left slot. Hint only: reads slots, draws text.
 */
public final class AnvilCalcModule extends Module {
    public static final String ID = "anvilcalc";

    final BoolSetting showButton = add(new BoolSetting("show_button", true));
    final BoolSetting slotLabels = add(new BoolSetting("slot_labels", true));
    final NumberSetting exactLimit = add(new NumberSetting("exact_limit", 10, 1, 12, 1));
    final NumberSetting tooExpensiveAt = add(new NumberSetting("too_expensive_at", 40, 2, 200, 1));
    final EnumSetting<AnvilProfile> rules = add(new EnumSetting<>("rules", AnvilProfile.AUTO));

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Skirmish AnvilCalc");
        thread.setDaemon(true);
        return thread;
    });
    private @Nullable AnvilOverlay overlay;

    public AnvilCalcModule() {
        super(ID, true);
    }

    /** The rules the next calculation uses (AUTO resolved against the current server). */
    RulesProfile rulesProfile() {
        return rules.get().resolve(HolyWorld.isConnected());
    }

    @Override
    public void onInitialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AnvilScreen anvil)) {
                return;
            }
            AnvilOverlay current = overlay;
            if (current == null || current.screen() != anvil) {
                current = new AnvilOverlay(this, anvil, executor);
                overlay = current;
            }
            // Per-screen events are recreated on every init/resize, so everything is registered again here.
            ScreenEvents.remove(anvil).register(removed -> {
                if (overlay != null && overlay.screen() == removed) {
                    overlay = null;
                }
            });
            current.attach();
        });
    }
}
