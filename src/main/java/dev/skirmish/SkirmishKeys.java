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
    /** «Сохранить момент»: the last seconds into the replay library. J is free in vanilla and sits next to K. */
    public static final KeyMapping KILLCAM_CLIP = key("key.skirmish.killcam.clip", GLFW.GLFW_KEY_J);
    public static final KeyMapping KILLCAM_LIBRARY = key("key.skirmish.killcam.library", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping CLANSHARE_SHARE = key("key.skirmish.clanshare.share", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping GEARINSPECTOR_LOCK = key("key.skirmish.gearinspector.lock", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping ANVILCALC_CALCULATE = key("key.skirmish.anvilcalc.calculate", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping KILLCARD_OPEN_FOLDER = key("key.skirmish.killcard.open_folder", InputConstants.UNKNOWN.getValue());
    /** Held, not pressed: panels in the «По клавише» detail mode (target card, events) show their full version. */
    public static final KeyMapping DETAILS = key("key.skirmish.details", GLFW.GLFW_KEY_LEFT_ALT);
    /** Hold to zoom. C like most zoom mods; vanilla uses C only for the creative "Save Hotbar Activator". */
    public static final KeyMapping ZOOM = key("key.skirmish.zoom", GLFW.GLFW_KEY_C);
    public static final KeyMapping FULLBRIGHT_TOGGLE = key("key.skirmish.fullbright.toggle", InputConstants.UNKNOWN.getValue());
    /** «Auto Sprint» on/off. Unbound by default. */
    public static final KeyMapping AUTO_SPRINT_TOGGLE = key("key.skirmish.auto_sprint.toggle", InputConstants.UNKNOWN.getValue());
    /** «Друзья»: add or remove the player under the crosshair. Unbound by default. */
    public static final KeyMapping FRIEND_TOGGLE = key("key.skirmish.friends.toggle", InputConstants.UNKNOWN.getValue());
    /** «Разбор боя»: review of the last fight (N is free in vanilla). */
    public static final KeyMapping FIGHT_REVIEW = key("key.skirmish.fight_review.open", GLFW.GLFW_KEY_N);
    /** «Меню игрока»: actions for the player under the crosshair (or a pick from the tab list). Unbound by default. */
    public static final KeyMapping PLAYER_MENU = key("key.skirmish.player_menu.open", InputConstants.UNKNOWN.getValue());

    /** «World Map»: full-screen map of the places you have been. M is free in vanilla. */
    public static final KeyMapping WORLD_MAP = key("key.skirmish.world_map", GLFW.GLFW_KEY_M);

    /** «Navigator»: route to the home waypoint (created here if missing). H is free in vanilla. */
    public static final KeyMapping NAVIGATOR_HOME = key("key.skirmish.navigator.home", GLFW.GLFW_KEY_H);

    /** «Base OS»: the base screen. B is free in vanilla. */
    public static final KeyMapping BASE_OS = key("key.skirmish.base_os.open", GLFW.GLFW_KEY_B);

    /** «Event Commander»: select the nearest event with a waypoint. G is free in vanilla. */
    public static final KeyMapping COMMANDER_GO = key("key.skirmish.commander.go", GLFW.GLFW_KEY_G);

    private SkirmishKeys() {
    }

    private static KeyMapping key(String name, int keyCode) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }

    static void register() {
        for (KeyMapping mapping : new KeyMapping[]{OPEN_MENU, WAYPOINT_ADD, WAYPOINT_CYCLE, KILLCAM_REPLAY, KILLCAM_CLIP, KILLCAM_LIBRARY, WORLD_MAP, NAVIGATOR_HOME, BASE_OS, COMMANDER_GO,
                CLANSHARE_SHARE, GEARINSPECTOR_LOCK, ANVILCALC_CALCULATE, KILLCARD_OPEN_FOLDER, DETAILS, ZOOM, FULLBRIGHT_TOGGLE, AUTO_SPRINT_TOGGLE,
                FRIEND_TOGGLE, FIGHT_REVIEW, PLAYER_MENU}) {
            KeyBindingHelper.registerKeyBinding(mapping);
        }
    }
}
