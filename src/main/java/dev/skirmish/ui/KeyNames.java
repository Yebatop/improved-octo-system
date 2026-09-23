package dev.skirmish.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

/** Compact key labels for keybind chips ("R-Shift" instead of "Right Shift"); other keys use vanilla names. */
public final class KeyNames {
    private static final Map<Integer, String> SHORT = Map.ofEntries(
            Map.entry(GLFW.GLFW_KEY_LEFT_SHIFT, "L-Shift"), Map.entry(GLFW.GLFW_KEY_RIGHT_SHIFT, "R-Shift"),
            Map.entry(GLFW.GLFW_KEY_LEFT_CONTROL, "L-Ctrl"), Map.entry(GLFW.GLFW_KEY_RIGHT_CONTROL, "R-Ctrl"),
            Map.entry(GLFW.GLFW_KEY_LEFT_ALT, "L-Alt"), Map.entry(GLFW.GLFW_KEY_RIGHT_ALT, "R-Alt"),
            Map.entry(GLFW.GLFW_KEY_LEFT_SUPER, "L-Super"), Map.entry(GLFW.GLFW_KEY_RIGHT_SUPER, "R-Super"));

    private KeyNames() {
    }

    public static String shortName(KeyMapping mapping) {
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        if (key.getType() == InputConstants.Type.KEYSYM) {
            String name = SHORT.get(key.getValue());
            if (name != null) {
                return name;
            }
        }
        return mapping.getTranslatedKeyMessage().getString();
    }
}
