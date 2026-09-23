package dev.skirmish;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Every key binding of the mod, registered once in the core so all of them appear under
 * "Skirmish" in Options → Controls. Modules poll {@link KeyMapping#consumeClick()} in {@code tick()}
 * (or use {@link KeyMapping#matches} inside screens). Translation keys live in assets/skirmish/lang.
 */
public final class SkirmishKeys {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skirmish", "main"));

    public static final KeyMapping OPEN_MENU = key("key.skirmish.open_menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
    public static final KeyMapping WAYPOINT_ADD = key("key.skirmish.waypoint_add", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping WAYPOINT_CYCLE = key("key.skirmish.waypoint_cycle", InputConstants.UNKNOWN.getValue());

    public static final KeyMapping KILLCAM_REPLAY = key("key.skirmish.killcam.replay", GLFW.GLFW_KEY_K);
    public static final KeyMapping CLANSHARE_SHARE = key("key.skirmish.clanshare.share", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping GEARINSPECTOR_LOCK = key("key.skirmish.gearinspector.lock", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping ANVILCALC_CALCULATE = key("key.skirmish.anvilcalc.calculate", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping KILLCARD_OPEN_FOLDER = key("key.skirmish.killcard.open_folder", InputConstants.UNKNOWN.getValue());

    private SkirmishKeys() {
    }

    private static KeyMapping key(String name, int keyCode) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }

    static void register() {
        for (KeyMapping mapping : new KeyMapping[]{OPEN_MENU, WAYPOINT_ADD, WAYPOINT_CYCLE, KILLCAM_REPLAY,
                CLANSHARE_SHARE, GEARINSPECTOR_LOCK, ANVILCALC_CALCULATE, KILLCARD_OPEN_FOLDER}) {
            KeyBindingHelper.registerKeyBinding(mapping);
        }
    }
}
