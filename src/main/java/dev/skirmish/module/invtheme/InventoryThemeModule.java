package dev.skirmish.module.invtheme;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * «Inventory Theme»: the player inventory, chests, barrels, shulker boxes, hoppers and dispensers drawn as a dark
 * rounded Skirmish panel with rounded slots and light titles instead of vanilla's grey texture. The layout, slots and
 * clicks are vanilla's. Server menus that paint their own background with font glyphs are left alone. Render-only;
 * Feature Control id {@code inventory_theme}.
 */
public final class InventoryThemeModule extends Module {
    public static final String ID = "inventory_theme";
    private static volatile @Nullable InventoryThemeModule instance;

    final BoolSetting inventory = add(new BoolSetting("inventory", true));
    final BoolSetting containers = add(new BoolSetting("containers", true));

    public InventoryThemeModule() {
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

    /** Whether this screen gets the Skirmish look now. */
    public static boolean themes(AbstractContainerScreen<?> screen) {
        InventoryThemeModule m = instance;
        if (m == null || !m.isEnabled()) {
            return false;
        }
        if (screen instanceof InventoryScreen) {
            return m.inventory.get();
        }
        return m.containers.get() && !customGui(screen.getTitle());
    }

    /**
     * A title with private-use glyphs: servers draw whole menu backgrounds with such characters from their resource
     * pack, so that menu keeps its own look.
     */
    static boolean customGui(Component title) {
        String text = title.getString();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\uE000' && c <= '\uF8FF') {
                return true;
            }
        }
        return false;
    }
}
