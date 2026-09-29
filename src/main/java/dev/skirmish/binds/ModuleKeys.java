package dev.skirmish.binds;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skirmish.module.Module;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One key binding per module that can be switched: «Вкл/выкл: Fullbright», unbound until you set one. They are
 * ordinary {@link KeyMapping}s in their own «Skirmish · модули» group of Options → Controls (and in the mod's binds
 * screen). Pressing one switches the module like its toggle in the menu and says so on screen.
 */
public final class ModuleKeys {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skirmish", "modules"));
    private static final Map<Module, KeyMapping> KEYS = new LinkedHashMap<>();

    private ModuleKeys() {
    }

    /** Registers the bindings (once, at start, before the options are read). */
    public static void register(List<Module> modules) {
        for (Module m : modules) {
            if (!m.canToggle() || "interface".equals(m.id()) || KEYS.containsKey(m)) {
                continue;
            }
            KeyMapping key = new KeyMapping("key.skirmish.toggle." + m.id(), InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(), CATEGORY);
            KeyBindingHelper.registerKeyBinding(key);
            KEYS.put(m, key);
        }
    }

    /** Module → its switch key, in menu order. */
    public static Map<Module, KeyMapping> all() {
        return Collections.unmodifiableMap(KEYS);
    }

    /** Switches the modules whose key was pressed (a module blocked by the server stays off). */
    public static void tick() {
        for (Map.Entry<Module, KeyMapping> e : KEYS.entrySet()) {
            while (e.getValue().consumeClick()) {
                Module m = e.getKey();
                if (m.isBlocked()) {
                    BindToast.show(m, false, true);
                    continue;
                }
                m.toggle();
                BindToast.show(m, m.isEnabled(), false);
            }
        }
    }
}
