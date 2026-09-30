package dev.skirmish.module.menus;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.StringSetting;
import org.jspecify.annotations.Nullable;

/**
 * «Main Menu»: Skirmish's own title screen instead of vanilla's — animated space with a quasar, the logo, a
 * «Играть на HolyWorld» button and rotating HolyWorld tips. Screen swap only; Feature Control id {@code main_menu}.
 */
public final class MainMenuModule extends Module {
    public static final String ID = "main_menu";
    private static volatile @Nullable MainMenuModule instance;

    final BoolSetting quasar = add(new BoolSetting("quasar", true));
    final BoolSetting tips = add(new BoolSetting("tips", true));
    final StringSetting address = add(new StringSetting("address", "mc.holyworld.ru"));

    public MainMenuModule() {
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
    public void onInitialize() {
        instance = this;
    }

    public static boolean active() {
        MainMenuModule m = instance;
        return m != null && m.isEnabled();
    }

    static boolean quasarOn() {
        MainMenuModule m = instance;
        return m == null || m.quasar.get();
    }

    static boolean tipsOn() {
        MainMenuModule m = instance;
        return m == null || m.tips.get();
    }

    static String serverAddress() {
        MainMenuModule m = instance;
        String a = m == null ? "" : m.address.get().trim();
        return a.isEmpty() ? "mc.holyworld.ru" : a;
    }
}
