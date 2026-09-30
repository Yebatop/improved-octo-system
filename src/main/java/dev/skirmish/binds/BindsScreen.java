package dev.skirmish.binds;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.Module;
import dev.skirmish.setting.StringSetting;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.IconButton;
import dev.skirmish.ui.widget.KeybindButton;
import dev.skirmish.ui.widget.Segmented;
import dev.skirmish.ui.widget.TextField;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.ui.widget.Widget;
import dev.skirmish.ui.widget.WindowFrame;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * «Бинды»: every key of the mod on one screen. On the left a keyboard (and the mouse buttons) with each key
 * coloured by what uses it — free, the game, Skirmish, or more than one thing (a conflict, in red); hover a key to
 * see its bindings, click it to list only those. On the right the mod's bindings — its actions and a switch for
 * every module — with search, a «только конфликты» filter, click-to-rebind and reset. They are the same vanilla
 * key bindings as in Options → Controls, saved to options.txt.
 */
public final class BindsScreen extends UiScreen {
    private static final String L = "layout.binds.";

    /** One key on the drawn keyboard: its label, GLFW key (or mouse button when {@code mouse}), row, column and width in units. */
    private record Cap(String label, int code, boolean mouse, int row, float col, float units) {
    }

    private static final List<Cap> KEYBOARD = keyboard();
    /** Examples in empty command slots. */
    private static final String[] HINTS = {"/warp pvp", "/home", "/spawn", "/ah", "/kit start", "/trade", "/warp mine",
            "/feed", "/rtp", "/clan home", "/vote", "/spec"};

    private final WindowFrame frame = new WindowFrame("binds", L);
    private final StringSetting query = new StringSetting("query", "", 32, false);
    private final TextField search = new TextField(query, () -> scroll = 0f).placeholder(() -> Ui.tr("skirmish.binds.search"));
    private int filter;
    private final Segmented filterPicker = new Segmented(Segmented.Spec.MENU,
            () -> List.of(Ui.tr("skirmish.binds.filter.all"), Ui.tr("skirmish.binds.filter.actions"),
                    Ui.tr("skirmish.binds.filter.modules"), Ui.tr("skirmish.binds.filter.commands"), Ui.tr("skirmish.binds.filter.conflicts")),
            () -> filter, i -> {
                filter = i;
                scroll = 0f;
            });
    private final Button done = new Button(() -> Ui.tr("skirmish.menu.done"), true, this::onClose);
    private final Map<KeyMapping, KeybindButton> buttons = new HashMap<>();
    private final Map<KeyMapping, IconButton> resets = new HashMap<>();
    private final Map<Integer, TextField> commandFields = new HashMap<>();
    private final Map<Integer, Button> modeButtons = new HashMap<>();
    private final List<CapWidget> caps = new ArrayList<>();
    private InputConstants.@Nullable Key picked;
    private float scroll;
    private float maxScroll;

    public BindsScreen(@Nullable Screen parent) {
        super(Component.translatable("skirmish.binds.title"), parent);
        for (Cap c : KEYBOARD) {
            caps.add(new CapWidget(c));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- data ----

    /** A binding of the mod: its mapping, its name, and whether it is a module switch. */
    private record Bind(KeyMapping mapping, String name, boolean module) {
    }

    private static List<Bind> modBinds() {
        List<Bind> out = new ArrayList<>();
        for (KeyMapping m : Minecraft.getInstance().options.keyMappings) {
            if (m.getCategory() == SkirmishKeys.CATEGORY || m == CommandBindsModule.WHEEL) {
                out.add(new Bind(m, Component.translatable(m.getName()).getString(), false));
            }
        }
        for (Map.Entry<Module, KeyMapping> e : ModuleKeys.all().entrySet()) {
            if (!e.getKey().isBlocked()) {
                out.add(new Bind(e.getValue(), Component.translatable(e.getKey().nameKey()).getString(), true));
            }
        }
        return out;
    }

    private static InputConstants.Key keyOf(KeyMapping m) {
        return InputConstants.getKey(m.saveString());
    }

    /**
     * Every binding on that key that can clash in play. Left out: the F3 + key debug combos (they only work with F3
     * held), and the creative and spectator bindings (other game modes).
     */
    private static List<KeyMapping> on(InputConstants.Key key) {
        List<KeyMapping> out = new ArrayList<>();
        if (key.equals(InputConstants.UNKNOWN)) {
            return out;
        }
        for (KeyMapping m : Minecraft.getInstance().options.keyMappings) {
            if (keyOf(m).equals(key) && counts(m)) {
                out.add(m);
            }
        }
        return out;
    }

    private static boolean counts(KeyMapping m) {
        KeyMapping.Category c = m.getCategory();
        if (c == KeyMapping.Category.DEBUG) {
            return m == Minecraft.getInstance().options.keyDebugOverlay;
        }
        return c != KeyMapping.Category.CREATIVE && c != KeyMapping.Category.SPECTATOR;
    }

    private static boolean isMod(KeyMapping m) {
        return m.getCategory() == SkirmishKeys.CATEGORY || m.getCategory() == ModuleKeys.CATEGORY
                || m.getCategory() == CommandBindsModule.CATEGORY;
    }

    private static String displayName(KeyMapping m) {
        CommandBindsModule commands = CommandBindsModule.instance();
        if (commands != null) {
            for (CommandBindsModule.Slot slot : commands.slots()) {
                if (slot.key() == m && !slot.command().get().isBlank()) {
                    return slot.command().get().strip();
                }
            }
        }
        for (Map.Entry<Module, KeyMapping> e : ModuleKeys.all().entrySet()) {
            if (e.getValue() == m) {
                return Ui.tr("skirmish.binds.toggle", Component.translatable(e.getKey().nameKey()).getString());
            }
        }
        return Component.translatable(m.getName()).getString();
    }

    // ---- drawing ----

    @Override
    protected void draw(Ui ui, double mx, double my) {
        ui.rect(0, 0, ui.width(), ui.height(), 0, ui.color("backdrop"));
        float stroke = ui.num("stroke.width");
        frame.layout(ui);
        widget(ui, frame.mover, mx, my);
        float w = frame.w();
        float h = frame.h();
        float ox = frame.x();
        float oy = frame.y() + Math.round((1f - appearProgress()) * ui.num("layout.appear_offset"));
        ui.box(ox, oy, w, h, ui.theme().radius("window"), ui.color("window"), ui.color("stroke_07"));

        float padX = ui.num("layout.menu.content_pad_x");
        float padY = ui.num("layout.menu.content_pad_y");
        float gap = ui.num("layout.menu.content_gap");
        float x = ox + stroke + padX;
        float cw = w - (stroke + padX) * 2;
        float y = oy + stroke + padY;
        ui.text("menu_title", Ui.tr("skirmish.binds.title"), x, y);
        y += ui.lineHeight("menu_title") + ui.num("layout.menu.header_text_gap");
        ui.text("menu_desc", ui.ellipsize("menu_desc", Ui.tr("skirmish.binds.subtitle"), cw), x, y);
        y += ui.lineHeight("menu_desc") + gap;

        float bh = done.preferredHeight(ui);
        float fy = oy + h - stroke - padY - bh;
        float dw = done.preferredWidth(ui);
        done.bounds(x + cw - dw, fy, dw, bh);
        widget(ui, done, mx, my);
        legend(ui, x, fy, bh);

        // Left: the keyboard; right: the list.
        float kbW = Math.min(cw * ui.num(L + "keyboard_share"), ui.num(L + "keyboard_max"));
        float listX = x + kbW + gap;
        float listW = cw - kbW - gap;
        float bottom = fy - gap;
        CapWidget hovered = drawKeyboard(ui, x, y, kbW, mx, my);
        drawList(ui, listX, y, listW, bottom, mx, my);
        if (hovered != null) {
            tooltip(ui, hovered, mx, my);
        }
        widget(ui, frame.grip, mx, my);
    }

    private @Nullable CapWidget drawKeyboard(Ui ui, float x, float y, float w, double mx, double my) {
        float unit = w / 15.5f;
        float gap = ui.num(L + "cap_gap");
        float capH = unit * 0.92f;
        CapWidget hovered = null;
        for (CapWidget cw : caps) {
            Cap c = cw.cap;
            float rowY = y + c.row() * (capH + gap) + (c.row() >= 1 ? gap * 2 : 0) + (c.row() >= 6 ? gap * 3 : 0);
            cw.bounds(x + c.col() * unit, rowY, c.units() * unit - gap, capH);
            widget(ui, cw, mx, my);
            if (cw.contains(mx, my)) {
                hovered = cw;
            }
        }
        float labelY = y + 6 * (capH + gap) + gap * 2 + (capH - ui.lineHeight("bind_cap")) / 2f;
        ui.text("bind_cap_hint", Ui.tr("skirmish.binds.mouse"), x, labelY);
        return hovered;
    }

    private void drawList(Ui ui, float x, float top, float w, float bottom, double mx, double my) {
        float fh = ui.num("layout.menu.keybind_height");
        float fgap = ui.num("layout.menu.footer_gap");
        float segH = filterPicker.preferredHeight(ui);
        search.bounds(x, top, w, fh);
        widget(ui, search, mx, my);
        float y = top + fh + fgap;
        filterPicker.bounds(x, y, Math.min(w, filterPicker.preferredWidth(ui)), segH);
        widget(ui, filterPicker, mx, my);
        y += segH + fgap;
        if (picked != null) {
            String label = Ui.tr("skirmish.binds.on_key", picked.getDisplayName().getString());
            ui.text("menu_row_desc", ui.ellipsize("menu_row_desc", label, w), x, y, ui.color("accent"));
            y += ui.lineHeight("menu_row_desc") + fgap;
        }
        ui.hline(x, y, w, ui.color("stroke"));
        y += ui.num("stroke.width");

        String q = query.get().strip().toLowerCase(Locale.ROOT);
        List<Bind> binds = new ArrayList<>();
        for (Bind b : modBinds()) {
            InputConstants.Key key = keyOf(b.mapping());
            boolean conflict = on(key).size() > 1;
            if (filter == 1 && b.module() || filter == 2 && !b.module() || filter == 3 || filter == 4 && !conflict) {
                continue;
            }
            if (picked != null && !key.equals(picked)) {
                continue;
            }
            if (!q.isEmpty() && !b.name().toLowerCase(Locale.ROOT).contains(q) && !key.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            binds.add(b);
        }

        pushClip(ui, x - 2, y, x + w + 2, bottom);
        float rowH = ui.num(L + "row_h");
        float ry = y - scroll + ui.num("layout.menu.rows_gap");
        boolean moduleHeader = false;
        boolean actionHeader = false;
        List<CommandBindsModule.Slot> slots = commandSlots(q);
        if (binds.isEmpty() && slots.isEmpty()) {
            ui.text("menu_row_desc", Ui.tr("skirmish.binds.empty"), x, ry + 4);
        }
        for (Bind b : binds) {
            if (!b.module() && !actionHeader) {
                actionHeader = true;
                ry += section(ui, Ui.tr("skirmish.binds.section.actions"), x, ry);
            }
            if (b.module() && !moduleHeader) {
                moduleHeader = true;
                ry += section(ui, Ui.tr("skirmish.binds.section.modules"), x, ry);
            }
            KeyMapping m = b.mapping();
            InputConstants.Key key = keyOf(m);
            List<KeyMapping> same = on(key);
            boolean conflict = same.size() > 1;
            KeybindButton button = buttons.computeIfAbsent(m, KeybindButton::new);
            IconButton reset = resets.computeIfAbsent(m, k -> new IconButton("fill_06", "rec_16", "button_sm", (u, btn) -> {
                float size = u.num(L + "reset_icon");
                float[] at = btn.iconAt(size);
                resetIcon(u, at[0], at[1], size, u.color(btn.enabled ? "text_2" : "text_3"));
            }, () -> {
                k.setKey(k.getDefaultKey());
                KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
            }));
            float kw = Math.max(ui.num(L + "key_w"), button.preferredWidth(ui));
            float resetW = ui.num("layout.menu.small_button_height");
            float kh = ui.num("layout.menu.keybind_height");
            reset.enabled = !m.isDefault();
            reset.bounds(x + w - resetW, ry + (rowH - kh) / 2f, resetW, kh);
            button.bounds(reset.x - fgap - kw, ry + (rowH - kh) / 2f, kw, kh);
            float textW = button.x - x - fgap;
            String name = b.module() ? Ui.tr("skirmish.binds.toggle", b.name()) : b.name();
            float nameY = ry + (rowH - ui.lineHeight("menu_row_title") - (conflict ? ui.lineHeight("menu_row_desc") : 0f)) / 2f;
            ui.text("menu_row_title", ui.ellipsize("menu_row_title", name, textW), x, nameY);
            if (conflict) {
                StringBuilder with = new StringBuilder();
                for (KeyMapping other : same) {
                    if (other != m) {
                        with.append(with.isEmpty() ? "" : ", ").append(displayName(other));
                    }
                }
                ui.text("menu_row_desc", ui.ellipsize("menu_row_desc", Ui.tr("skirmish.binds.conflict", with), textW),
                        x, nameY + ui.lineHeight("menu_row_title"), ui.color("bad"));
            }
            widget(ui, button, mx, my);
            widget(ui, reset, mx, my);
            ry += rowH;
        }
        if (!slots.isEmpty()) {
            ry += section(ui, Ui.tr("skirmish.binds.section.commands"), x, ry);
        }
        for (CommandBindsModule.Slot slot : slots) {
            ry = commandRow(ui, slot, x, ry, w, rowH, mx, my);
        }
        popClip(ui);
        maxScroll = Math.max(0f, ry + scroll - bottom);
        scroll = Math.max(0f, Math.min(scroll, maxScroll));
    }

    /**
     * Command slots to list: all of them under «Команды»; under «Все» the filled ones and one empty to add a command;
     * narrowed by the picked key, the search and «Конфликты» like the rest.
     */
    private List<CommandBindsModule.Slot> commandSlots(String q) {
        CommandBindsModule commands = CommandBindsModule.instance();
        List<CommandBindsModule.Slot> out = new ArrayList<>();
        if (commands == null || commands.isBlocked() || filter == 1 || filter == 2) {
            return out;
        }
        boolean emptyShown = false;
        for (CommandBindsModule.Slot slot : commands.slots()) {
            if (slot.key() == null) {
                continue;
            }
            InputConstants.Key key = keyOf(slot.key());
            String text = slot.command().get();
            if (picked != null && !key.equals(picked) || filter == 4 && on(key).size() < 2) {
                continue;
            }
            if (!q.isEmpty() && !text.toLowerCase(Locale.ROOT).contains(q) && !key.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            if (text.isBlank() && key.equals(InputConstants.UNKNOWN) && filter == 0) {
                if (emptyShown) {
                    continue;
                }
                emptyShown = true;
            }
            out.add(slot);
        }
        return out;
    }

    /** One command slot: the command to type, «Сразу» / «В чат», its key; a conflict line under it. */
    private float commandRow(Ui ui, CommandBindsModule.Slot slot, float x, float ry, float w, float rowH, double mx, double my) {
        float fgap = ui.num("layout.menu.footer_gap");
        float kh = ui.num("layout.menu.keybind_height");
        KeybindButton button = buttons.computeIfAbsent(slot.key(), KeybindButton::new);
        TextField field = commandFields.computeIfAbsent(slot.index(), i -> new TextField(slot.command(), () -> { })
                .placeholder(() -> HINTS[slot.index() % HINTS.length]));
        Button mode = modeButtons.computeIfAbsent(slot.index(), i -> new Button(
                () -> Ui.tr(slot.send().get() ? "skirmish.binds.mode.send" : "skirmish.binds.mode.insert"), false,
                () -> slot.send().set(!slot.send().get())).layout("layout.menu.small_"));
        float kw = Math.max(ui.num(L + "key_w"), button.preferredWidth(ui));
        float modeW = ui.num(L + "mode_w");
        float cy = ry + (rowH - kh) / 2f;
        button.bounds(x + w - kw, cy, kw, kh);
        mode.bounds(button.x - fgap - modeW, cy, modeW, kh);
        field.bounds(x, cy, mode.x - fgap - x, kh);
        widget(ui, field, mx, my);
        widget(ui, mode, mx, my);
        widget(ui, button, mx, my);
        List<KeyMapping> same = on(keyOf(slot.key()));
        if (same.size() > 1) {
            StringBuilder with = new StringBuilder();
            for (KeyMapping other : same) {
                if (other != slot.key()) {
                    with.append(with.isEmpty() ? "" : ", ").append(displayName(other));
                }
            }
            ui.text("menu_row_desc", ui.ellipsize("menu_row_desc", Ui.tr("skirmish.binds.conflict", with), w),
                    x, cy + kh + 1, ui.color("bad"));
            return ry + rowH + ui.lineHeight("menu_row_desc");
        }
        return ry + rowH;
    }

    /** Circular arrow (back to the default key). */
    private static void resetIcon(Ui ui, float x, float y, float size, int color) {
        float c = size / 2f;
        ui.ring(x + c, y + c, size * 0.75f, 1.8f, color);
        ui.rect(x + c - size * 0.1f, y + c - size * 0.5f, size * 0.3f, size * 0.25f, 0f, ui.color("fill_06"));
        ui.triangle(x + c + size * 0.05f, y, x + c + size * 0.05f, y + size * 0.36f, x + c + size * 0.35f, y + size * 0.18f, 0.5f, color);
    }

    private static float section(Ui ui, String label, float x, float y) {
        float h = ui.lineHeight("event_section") + ui.num("layout.menu.rows_gap") * 2;
        ui.text("event_section", label.toUpperCase(Locale.ROOT), x, y + ui.num("layout.menu.rows_gap"));
        return h;
    }

    private static void legend(Ui ui, float x, float y, float h) {
        float s = ui.num(L + "legend_dot");
        float cx = x;
        for (String[] item : new String[][]{{"bind_free", "skirmish.binds.legend.free"}, {"bind_game", "skirmish.binds.legend.game"},
                {"bind_mod", "skirmish.binds.legend.mod"}, {"bind_conflict", "skirmish.binds.legend.conflict"}}) {
            ui.rect(cx, y + (h - s) / 2f, s, s, s / 3f, ui.color(item[0]));
            cx += s + 6;
            String label = Ui.tr(item[1]);
            ui.textCentered("menu_hint", label, cx, y, h);
            cx += ui.textWidth("menu_hint", label) + 16;
        }
    }

    private void tooltip(Ui ui, CapWidget cap, double mx, double my) {
        List<KeyMapping> on = on(cap.key());
        List<String> lines = new ArrayList<>();
        lines.add(cap.key().getDisplayName().getString());
        if (on.isEmpty()) {
            lines.add(Ui.tr("skirmish.binds.legend.free"));
        }
        for (KeyMapping m : on) {
            lines.add((isMod(m) ? "◆ " : "· ") + displayName(m));
        }
        float pad = 8;
        float lh = ui.lineHeight("menu_row_desc");
        float tw = 0;
        for (String l : lines) {
            tw = Math.max(tw, ui.textWidth("menu_row_desc", l));
        }
        float tx = (float) Math.min(mx + 14, ui.width() - tw - pad * 2 - 4);
        float ty = (float) Math.min(my + 14, ui.height() - lines.size() * lh - pad * 2 - 4);
        ui.box(tx, ty, tw + pad * 2, lines.size() * lh + pad * 2, 8f, ui.color("window"), ui.color("stroke"));
        for (int i = 0; i < lines.size(); i++) {
            ui.text("menu_row_desc", lines.get(i), tx + pad, ty + pad + i * lh, ui.color(i == 0 ? "text" : on.size() > 1 ? "bad" : "text_2"));
        }
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) scrollY * dev.skirmish.ui.Theme.get().num("layout.menu.scroll_step")));
        return true;
    }

    /** A key of the drawn keyboard: coloured by what is bound to it; a click lists only its bindings. */
    private final class CapWidget extends Widget {
        final Cap cap;

        CapWidget(Cap cap) {
            this.cap = cap;
        }

        InputConstants.Key key() {
            return cap.mouse() ? InputConstants.Type.MOUSE.getOrCreate(cap.code()) : InputConstants.Type.KEYSYM.getOrCreate(cap.code());
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            List<KeyMapping> on = on(key());
            boolean mod = on.stream().anyMatch(BindsScreen::isMod);
            String tone = on.size() > 1 ? "bind_conflict" : mod ? "bind_mod" : on.isEmpty() ? "bind_free" : "bind_game";
            int fill = Anim.lerpColor(ui.color(tone), ui.color("fill_10"), hovered() * 0.5f);
            float r = ui.num(L + "cap_r");
            ui.rect(x, y, w, h, r, fill);
            boolean selected = key().equals(picked);
            if (selected) {
                ui.border(x, y, w, h, r, 2f, ui.color("accent"));
            }
            String style = cap.label().length() > 2 ? "bind_cap_small" : "bind_cap";
            float tw = ui.textWidth(style, cap.label());
            int text = ui.color(on.isEmpty() ? "text_3" : "text");
            ui.textCentered(style, cap.label(), x + (w - tw) / 2f, y, h, text);
            if (mod && on.size() == 1) {
                ui.circle(x + w - 5, y + 5, 4, ui.color("accent"));
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0) {
                return false;
            }
            picked = key().equals(picked) ? null : key();
            scroll = 0f;
            return true;
        }
    }

    // ---- the keyboard ----

    private static List<Cap> keyboard() {
        List<Cap> out = new ArrayList<>();
        float c = 0;
        out.add(new Cap("Esc", GLFW.GLFW_KEY_ESCAPE, false, 0, 0, 1));
        for (int i = 1; i <= 12; i++) {
            float col = 1.5f + (i - 1) + ((i - 1) / 4) * 0.5f;
            out.add(new Cap("F" + i, GLFW.GLFW_KEY_F1 + i - 1, false, 0, col, 1));
        }
        String row1 = "`1234567890-=";
        int[] row1Codes = {GLFW.GLFW_KEY_GRAVE_ACCENT, GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5,
                GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL};
        for (int i = 0; i < row1.length(); i++) {
            out.add(new Cap(String.valueOf(row1.charAt(i)), row1Codes[i], false, 1, i, 1));
        }
        out.add(new Cap("Bksp", GLFW.GLFW_KEY_BACKSPACE, false, 1, 13, 2));
        out.add(new Cap("Tab", GLFW.GLFW_KEY_TAB, false, 2, 0, 1.5f));
        c = 1.5f;
        for (char ch : "QWERTYUIOP".toCharArray()) {
            out.add(new Cap(String.valueOf(ch), GLFW.GLFW_KEY_A + (ch - 'A'), false, 2, c, 1));
            c += 1;
        }
        out.add(new Cap("[", GLFW.GLFW_KEY_LEFT_BRACKET, false, 2, c, 1));
        out.add(new Cap("]", GLFW.GLFW_KEY_RIGHT_BRACKET, false, 2, c + 1, 1));
        out.add(new Cap("\\", GLFW.GLFW_KEY_BACKSLASH, false, 2, c + 2, 1.5f));
        out.add(new Cap("Caps", GLFW.GLFW_KEY_CAPS_LOCK, false, 3, 0, 1.75f));
        c = 1.75f;
        for (char ch : "ASDFGHJKL".toCharArray()) {
            out.add(new Cap(String.valueOf(ch), GLFW.GLFW_KEY_A + (ch - 'A'), false, 3, c, 1));
            c += 1;
        }
        out.add(new Cap(";", GLFW.GLFW_KEY_SEMICOLON, false, 3, c, 1));
        out.add(new Cap("'", GLFW.GLFW_KEY_APOSTROPHE, false, 3, c + 1, 1));
        out.add(new Cap("Enter", GLFW.GLFW_KEY_ENTER, false, 3, c + 2, 2.25f));
        out.add(new Cap("Shift", GLFW.GLFW_KEY_LEFT_SHIFT, false, 4, 0, 2.25f));
        c = 2.25f;
        for (char ch : "ZXCVBNM".toCharArray()) {
            out.add(new Cap(String.valueOf(ch), GLFW.GLFW_KEY_A + (ch - 'A'), false, 4, c, 1));
            c += 1;
        }
        out.add(new Cap(",", GLFW.GLFW_KEY_COMMA, false, 4, c, 1));
        out.add(new Cap(".", GLFW.GLFW_KEY_PERIOD, false, 4, c + 1, 1));
        out.add(new Cap("/", GLFW.GLFW_KEY_SLASH, false, 4, c + 2, 1));
        out.add(new Cap("R-Shift", GLFW.GLFW_KEY_RIGHT_SHIFT, false, 4, c + 3, 2.75f));
        out.add(new Cap("Ctrl", GLFW.GLFW_KEY_LEFT_CONTROL, false, 5, 0, 1.5f));
        out.add(new Cap("Win", GLFW.GLFW_KEY_LEFT_SUPER, false, 5, 1.5f, 1.25f));
        out.add(new Cap("Alt", GLFW.GLFW_KEY_LEFT_ALT, false, 5, 2.75f, 1.25f));
        out.add(new Cap("Space", GLFW.GLFW_KEY_SPACE, false, 5, 4f, 6.5f));
        out.add(new Cap("R-Alt", GLFW.GLFW_KEY_RIGHT_ALT, false, 5, 10.5f, 1.5f));
        out.add(new Cap("R-Ctrl", GLFW.GLFW_KEY_RIGHT_CONTROL, false, 5, 12f, 1.5f));
        out.add(new Cap("←", GLFW.GLFW_KEY_LEFT, false, 5, 13.5f, 0.66f));
        out.add(new Cap("↑", GLFW.GLFW_KEY_UP, false, 5, 14.16f, 0.67f));
        out.add(new Cap("→", GLFW.GLFW_KEY_RIGHT, false, 5, 14.83f, 0.67f));
        // Mouse: left, right, middle, side buttons.
        String[] mouse = {"ЛКМ", "ПКМ", "СКМ", "M4", "M5"};
        for (int i = 0; i < mouse.length; i++) {
            out.add(new Cap(mouse[i], i, true, 6, 2.5f + i * 1.6f, 1.5f));
        }
        return out;
    }
}
