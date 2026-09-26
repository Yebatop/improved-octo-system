package dev.skirmish.module.hwos;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.KeySetting;
import net.minecraft.client.Minecraft;

/**
 * «HolyWorld OS»: HolyWorld in one app window (key O) — events, economy, anarchies, a guide and your profile, from
 * what Skirmish already knows. UI only; Feature Control id {@code holyworld_os}.
 */
public final class HwOsModule extends Module {
    public static final String ID = "holyworld_os";
    static final String L = "layout.hwos.";

    final KeySetting openKey = add(new KeySetting("open_key", "key.skirmish.hwos.open"));

    public HwOsModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.INTERFACE;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (SkirmishKeys.HWOS_OPEN.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new HwOsScreen(null));
            }
        }
    }
}
