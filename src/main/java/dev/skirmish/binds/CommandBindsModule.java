package dev.skirmish.binds;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.ActionSetting;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.StringSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * «Command Binds»: your own chat commands on keys — «/warp pvp», «/home», «/ah» — in {@link #SLOTS} slots. Each slot
 * is an ordinary key binding («Команда 1» … in Options → Controls and in the binds screen) with the text you typed.
 * One press sends it once, like typing it and pressing Enter (or, if you choose, only puts it into the chat box for
 * you to send). Nothing is sent on its own: no timers, no repeats, nothing in answer to the game. The server can
 * switch this off through Feature Control ({@code command_binds}).
 */
public final class CommandBindsModule extends Module {
    public static final String ID = "command_binds";
    public static final int SLOTS = 12;
    /** A second press of the same slot within this is ignored (a key bounce, not a spam tool). */
    static final long GUARD_MS = 600;
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skirmish", "commands"));
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static @Nullable CommandBindsModule instance;

    /** One slot: its command, whether a press sends it at once, and its key. */
    public record Slot(int index, StringSetting command, BoolSetting send, KeyMapping key) {
    }

    private final List<Slot> slots = new ArrayList<>();
    private final long[] lastSent = new long[SLOTS];

    public CommandBindsModule() {
        super(ID, true);
        add(new ActionSetting("open_binds", () -> Minecraft.getInstance().setScreen(new BindsScreen(Minecraft.getInstance().screen))));
        for (int i = 0; i < SLOTS; i++) {
            StringSetting command = add(new StringSetting("command_" + (i + 1), "", 256, false));
            BoolSetting send = add(new BoolSetting("send_" + (i + 1), true));
            command.visibleWhen(() -> false);
            send.visibleWhen(() -> false);
            slots.add(new Slot(i, command, send, KEYS.isEmpty() ? null : KEYS.get(i)));
        }
    }

    /** Registers the slots' key bindings (once, at start). */
    public static void registerKeys() {
        if (!KEYS.isEmpty()) {
            return;
        }
        for (int i = 1; i <= SLOTS; i++) {
            KeyMapping key = new KeyMapping("key.skirmish.command." + i, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
            KeyBindingHelper.registerKeyBinding(key);
            KEYS.add(key);
        }
    }

    @Override
    public Category category() {
        return Category.UTILITY;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        instance = this;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (s.key() == null && i < KEYS.size()) {
                slots.set(i, new Slot(i, s.command(), s.send(), KEYS.get(i)));
            }
        }
    }

    public static @Nullable CommandBindsModule instance() {
        return instance;
    }

    public List<Slot> slots() {
        return slots;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        for (Slot s : slots) {
            if (s.key() == null) {
                continue;
            }
            while (s.key().consumeClick()) {
                if (mc.screen == null && mc.player != null) {
                    run(mc, s);
                }
            }
        }
    }

    private void run(Minecraft mc, Slot s) {
        String text = s.command().get().strip();
        if (text.isEmpty() || mc.player == null) {
            return;
        }
        long now = Util.getMillis();
        if (now - lastSent[s.index()] < GUARD_MS) {
            return;
        }
        lastSent[s.index()] = now;
        if (!s.send().get()) {
            mc.setScreen(new ChatScreen(text, false));
            return;
        }
        // As if typed and sent from the chat box (client commands such as /skirmish still stay on the client).
        mc.gui.getChat().addRecentChat(text);
        if (text.startsWith("/")) {
            mc.player.connection.sendCommand(text.substring(1));
        } else {
            mc.player.connection.sendChat(text);
        }
        log("sent slot %d: %s", s.index() + 1, text);
        BindToast.showCommand(text);
    }
}
