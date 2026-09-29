package dev.skirmish.module.tablist;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import org.jspecify.annotations.Nullable;

/**
 * «Tab List»: the player list (Tab) as a Skirmish panel: the server's header and footer as they are, how many are
 * online and your ping, then the players in columns — head, name with the server's prefixes and colours, the list
 * score when the server shows one, ping in milliseconds coloured by how good it is. Your row and your friends' rows
 * are marked. Vanilla still decides when it shows. Render-only; Feature Control id {@code tab_list}.
 */
public final class TabListModule extends Module {
    public static final String ID = "tab_list";
    private static @Nullable TabListModule instance;

    final BoolSetting heads = add(new BoolSetting("heads", true));
    final BoolSetting friends = add(new BoolSetting("friends", true));
    final NumberSetting opacity = add(new NumberSetting("opacity", 90, 30, 100, 5).unit("%"));

    public TabListModule() {
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

    static @Nullable TabListModule active() {
        TabListModule m = instance;
        return m != null && m.isEnabled() ? m : null;
    }
}
