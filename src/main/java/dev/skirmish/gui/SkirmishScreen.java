package dev.skirmish.gui;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.ModuleManager;
import dev.skirmish.setting.ActionSetting;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.setting.Setting;
import dev.skirmish.setting.StringSetting;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.KeyNames;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.KeybindButton;
import dev.skirmish.ui.widget.Segmented;
import dev.skirmish.ui.widget.Slider;
import dev.skirmish.ui.widget.TextField;
import dev.skirmish.ui.widget.Toggle;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.ui.widget.Widget;
import dev.skirmish.ui.widget.WindowFrame;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main menu (mockup «Меню мода»): a movable, resizable window ({@link WindowFrame}, 860×520 by default), modules
 * grouped by {@link Category} in collapsible sections of a scrollable sidebar, the selected module's settings on
 * the right, built from its {@link Setting}s: Bool → toggle, Number → slider, Enum → segmented, String → text
 * field, Action → button, Key → keybind button.
 */
public final class SkirmishScreen extends UiScreen {
    private static final String L = "layout.menu.";
    private static String lastSelected = "";

    private final List<Module> modules;
    private final Map<Module, ModuleRow> moduleRows = new IdentityHashMap<>();
    private final Map<Category, CategoryRow> categoryRows = new EnumMap<>(Category.class);
    private final WindowFrame frame = new WindowFrame("menu", L);
    private final Map<Setting<?>, Widget> controls = new IdentityHashMap<>();
    private final Map<Setting<?>, GroupChevron> chevrons = new IdentityHashMap<>();
    /** Height of each open group's sub-rows as last drawn, for the open/close animation. */
    private final Map<Setting<?>, Float> groupHeights = new IdentityHashMap<>();
    private final Toggle moduleToggle;
    private final Button reset;
    private final Button done;
    private Module selected;
    private float scroll;
    private float maxScroll;
    private float sideScroll;
    private float sideMaxScroll;
    /** Sidebar list area in design px (x0, y0, x1, y1), for wheel routing. */
    private float[] sideArea = new float[4];

    public SkirmishScreen(@Nullable Screen parent) {
        super(Component.translatable("skirmish.menu.title"), parent);
        List<Module> all = new ArrayList<>(ModuleManager.get().all());
        all.sort(Comparator.comparingInt(Module::menuOrder));
        this.modules = List.copyOf(all);
        this.selected = modules.stream().filter(m -> m.id().equals(lastSelected)).findFirst().orElse(modules.getFirst());
        for (Module module : modules) {
            moduleRows.put(module, new ModuleRow(module));
        }
        for (Category category : Category.values()) {
            categoryRows.put(category, new CategoryRow(category));
        }
        this.moduleToggle = new Toggle(() -> selected.isEnabled(), value -> selected.setEnabled(value));
        this.reset = new Button(() -> Ui.tr("skirmish.menu.reset"), false, this::resetModule);
        this.done = new Button(() -> Ui.tr("skirmish.menu.done"), true, this::onClose);
    }

    /** Opens the menu on a given module (e.g. from the HUD editor). */
    public static void selectNext(String moduleId) {
        lastSelected = moduleId;
    }

    private void select(Module module) {
        if (module != selected) {
            selected = module;
            lastSelected = module.id();
            controls.clear();
            chevrons.clear();
            groupHeights.clear();
            scroll = 0;
        }
    }

    private void resetModule() {
        for (Setting<?> setting : selected.settings()) {
            if (setting instanceof KeySetting key) {
                KeyMapping mapping = KeyMapping.get(key.mappingName());
                if (mapping != null) {
                    mapping.setKey(mapping.getDefaultKey());
                }
            } else if (!(setting instanceof ActionSetting)) {
                setting.reset();
            }
        }
        selected.debugLog.reset();
        selected.setEnabled(selected.defaultEnabled());
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    @Override
    protected void onClosing() {
        ModuleManager.get().markDirty();
    }

    /** Modules not blocked by Feature Control (blocked ones must not even be mentioned). */
    private List<Module> visibleModules() {
        return modules.stream().filter(m -> !m.isBlocked()).toList();
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        if (selected.isBlocked()) {
            List<Module> visible = visibleModules();
            if (!visible.isEmpty()) {
                select(visible.getFirst());
            }
        }
        float t = appearProgress();
        ui.rect(0, 0, ui.width(), ui.height(), 0, ui.color("backdrop"));

        float stroke = ui.num("stroke.width");
        frame.layout(ui);
        // Empty spots of the window drag it; every control drawn later sits above this.
        widget(ui, frame.mover, mx, my);
        float cw = frame.w() - stroke * 2;
        float ch = frame.h() - stroke * 2;
        float ox = frame.x();
        float oy = frame.y() + Math.round((1f - t) * ui.num("layout.appear_offset"));
        ui.box(ox, oy, cw + stroke * 2, ch + stroke * 2, ui.theme().radius("window"), ui.color("window"), ui.color("stroke_07"));
        float x = ox + stroke;
        float y = oy + stroke;

        float sidebar = ui.num(L + "sidebar_width");
        drawSidebar(ui, x, y, ch, mx, my);
        drawContent(ui, x + sidebar, y, cw - sidebar, ch, mx, my);
        widget(ui, frame.grip, mx, my);
    }

    // ---- sidebar ----

    private void drawSidebar(Ui ui, float x, float y, float h, double mx, double my) {
        float sw = ui.num(L + "sidebar_width");
        float stroke = ui.num("stroke.width");
        float inner = ui.theme().radius("window") - stroke;
        // Left corners follow the window's rounding: a wider rounded rect clipped at the sidebar edge.
        pushClip(ui, x, y, x + sw, y + h);
        ui.rect(x, y, sw + inner * 2, h, inner, ui.color("sidebar"));
        popClip(ui);
        ui.rect(x + sw - stroke, y, stroke, h, 0, ui.color("stroke"));

        float padX = ui.num(L + "sidebar_pad_x");
        float padY = ui.num(L + "sidebar_pad_y");
        float gap = ui.num(L + "sidebar_gap");
        float cx = x + padX;
        float cwid = sw - stroke - padX * 2;
        float cy = y + padY;

        // Brand: logo tile, name and version.
        float bx = cx + ui.num(L + "brand_pad_x");
        float by = cy + ui.num(L + "brand_pad_top");
        float textH = ui.lineHeight("menu_brand") + ui.lineHeight("menu_brand_sub");
        float logo = ui.num(L + "logo");
        float rowH = Math.max(logo, textH);
        float ly = by + (rowH - logo) / 2f;
        ui.rect(bx, ly, logo, logo, ui.theme().radius("logo"), ui.color("accent"));
        float icon = ui.num(L + "logo_icon");
        Icons.sword(ui, bx + (logo - icon) / 2f, ly + (logo - icon) / 2f, icon, ui.num(L + "logo_icon_stroke"), ui.color("white"), false);
        float tx = bx + logo + ui.num(L + "brand_gap");
        float ty = by + (rowH - textH) / 2f;
        ui.text("menu_brand", "Skirmish", tx, ty);
        ui.text("menu_brand_sub", Ui.tr("skirmish.menu.brand_sub", SharedConstants.getCurrentVersion().name()), tx, ty + ui.lineHeight("menu_brand"));
        cy = by + rowH + ui.num(L + "brand_pad_bottom") + gap;

        // Footer hint (bottom aligned): key chip + "open menu".
        float hintPad = ui.num(L + "hint_pad");
        float chipPadX = ui.num(L + "hint_key_pad_x");
        float chipPadY = ui.num(L + "hint_key_pad_y");
        String key = SkirmishKeys.OPEN_MENU.isUnbound() ? Ui.tr("skirmish.ui.keybind_none") : KeyNames.shortName(SkirmishKeys.OPEN_MENU);
        float chipH = ui.lineHeight("menu_hint_key") + chipPadY * 2 + stroke * 2;
        float hintH = Math.max(chipH, ui.lineHeight("menu_hint"));
        float hy = y + h - padY - hintPad - hintH;
        float chipW = ui.textWidth("menu_hint_key", key) + chipPadX * 2 + stroke * 2;
        float hx = cx + hintPad;
        float chipY = hy + (hintH - chipH) / 2f;
        ui.border(hx, chipY, chipW, chipH, ui.theme().radius("chip"), stroke, ui.color("stroke_10"));
        ui.text("menu_hint_key", key, hx + stroke + chipPadX, chipY + stroke + chipPadY);
        ui.textCentered("menu_hint", Ui.tr("skirmish.menu.hint_open"), hx + chipW + ui.num(L + "hint_gap"), hy, hintH);

        float listBottom = hy - hintPad - gap;
        float moduleH = ui.num(L + "module_height");
        float categoryH = ui.num(L + "category_height");
        float top = cy;
        sideArea = new float[]{x, top, x + sw, listBottom};
        pushClip(ui, cx, top, cx + cwid, listBottom);
        float ly2 = top - sideScroll;
        List<Module> visible = visibleModules();
        for (Category category : Category.values()) {
            List<Module> members = visible.stream().filter(m -> m.category() == category).toList();
            if (members.isEmpty()) {
                continue;
            }
            CategoryRow header = categoryRows.get(category);
            header.count = members.size();
            header.bounds(cx, ly2, cwid, categoryH);
            widget(ui, header, mx, my);
            ly2 += categoryH + gap;
            if (header.collapsed()) {
                continue;
            }
            for (Module module : members) {
                ModuleRow row = moduleRows.get(module);
                row.bounds(cx, ly2, cwid, moduleH);
                widget(ui, row, mx, my);
                ly2 += moduleH + gap;
            }
        }
        popClip(ui);
        sideMaxScroll = Math.max(0f, ly2 + sideScroll - top - (listBottom - top));
        sideScroll = Math.max(0f, Math.min(sideScroll, sideMaxScroll));
    }

    /** Collapsible group header: «БОЙ · 5» with a chevron; the state is kept with the window. */
    private final class CategoryRow extends Widget {
        private final Category category;
        private final Anim open = new Anim("hover_ms");
        int count;

        CategoryRow(Category category) {
            this.category = category;
            open.snap(collapsed() ? 0f : 1f);
        }

        boolean collapsed() {
            return frame.flag("collapsed:" + category.name());
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            float padX = ui.num(L + "section_pad_x");
            float o = open.target(collapsed() ? 0f : 1f).value();
            int color = Anim.lerpColor(ui.color("text_3"), ui.color("text_2"), hovered());
            float chev = ui.num(L + "category_chevron");
            float cx = x + padX + chev / 2f;
            float cy = y + h / 2f;
            float lw = ui.num("stroke.width") * 1.5f;
            // Chevron: right-pointing when collapsed, turning down when open.
            double a = Math.toRadians(90 * o);
            float dx = chev / 2f;
            float dy = chev / 4f;
            float[][] pts = {{-dy, -dx}, {dy, 0}, {-dy, dx}};
            float[][] r = new float[3][2];
            for (int i = 0; i < 3; i++) {
                r[i][0] = cx + (float) (pts[i][0] * Math.cos(a) - pts[i][1] * Math.sin(a));
                r[i][1] = cy + (float) (pts[i][0] * Math.sin(a) + pts[i][1] * Math.cos(a));
            }
            ui.line(r[0][0], r[0][1], r[1][0], r[1][1], lw, color);
            ui.line(r[1][0], r[1][1], r[2][0], r[2][1], lw, color);
            String label = Ui.tr(category.translationKey());
            float tx = x + padX + chev + ui.num(L + "category_gap");
            ui.textCentered("menu_section", label, tx, y, h, color);
            String n = Integer.toString(count);
            ui.textCentered("menu_section", n, x + w - padX - ui.textWidth("menu_section", n), y, h, ui.color("text_4"));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                frame.setFlag("collapsed:" + category.name(), !collapsed());
                return true;
            }
            return false;
        }
    }

    private final class ModuleRow extends Widget {
        private final Module module;
        private final Anim active = new Anim("hover_ms");

        ModuleRow(Module module) {
            this.module = module;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            float a = active.target(module == selected).value();
            int idle = Anim.lerpColor(ui.color("fill_00"), ui.color("fill_04"), hovered());
            ui.rect(x, y, w, h, ui.theme().radius("button"), Anim.lerpColor(idle, ui.color("accent_16"), a));
            float padX = ui.num(L + "module_pad_x");
            float dot = ui.num(L + "dot");
            float textW = w - padX * 2 - ui.num(L + "module_gap") - dot;
            String name = ui.ellipsize("menu_module", Texts.tr(module.nameKey(), Texts.humanize(module.id())).getString(), textW);
            int color = Anim.lerpColor(Anim.lerpColor(ui.color("text_2"), ui.color("text"), hovered()), ui.color("white"), a);
            ui.textCentered("menu_module", name, x + padX, y, h, color);
            ui.circle(x + w - padX - dot / 2f, y + h / 2f, dot, module.isEnabled() ? ui.color("good") : ui.color("dot_off"));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                select(module);
                return true;
            }
            return false;
        }
    }

    // ---- content ----

    private void drawContent(Ui ui, float x, float y, float w, float h, double mx, double my) {
        float padX = ui.num(L + "content_pad_x");
        float padY = ui.num(L + "content_pad_y");
        float gap = ui.num(L + "content_gap");
        float cx = x + padX;
        float cw = w - padX * 2;
        float cy = y + padY;

        // Header: title + description, module switch on the right.
        float toggleW = ui.num(L + "toggle_width");
        float textW = cw - toggleW - ui.num(L + "header_gap");
        ui.text("menu_title", Texts.tr(selected.nameKey(), Texts.humanize(selected.id())).getString(), cx, cy);
        float ty = cy + ui.lineHeight("menu_title") + ui.num(L + "header_text_gap");
        for (String line : ui.wrap("menu_desc", Texts.tr(selected.descriptionKey(), "").getString(), textW)) {
            ui.text("menu_desc", line, cx, ty);
            ty += ui.lineHeight("menu_desc");
        }
        moduleToggle.enabled = selected.canToggle();
        moduleToggle.at(cx + cw - toggleW, cy);
        widget(ui, moduleToggle, mx, my);
        cy = Math.max(ty, cy + ui.num(L + "toggle_height")) + gap;
        ui.hline(cx, cy, cw, ui.color("stroke"));
        cy += ui.num("stroke.width") + gap;

        // Footer buttons, right aligned.
        float bh = done.preferredHeight(ui);
        float fy = y + h - padY - bh;
        float doneW = done.preferredWidth(ui);
        float resetW = reset.preferredWidth(ui);
        done.bounds(cx + cw - doneW, fy, doneW, bh);
        reset.bounds(done.x - ui.num(L + "footer_gap") - resetW, fy, resetW, bh);
        widget(ui, reset, mx, my);
        widget(ui, done, mx, my);

        // Settings rows, scrollable between the divider and the footer.
        float top = cy;
        float bottom = fy - gap;
        pushClip(ui, x, top, x + w, bottom);
        float content = drawRows(ui, cx, top - scroll, cw, mx, my);
        popClip(ui);
        maxScroll = Math.max(0f, content - (bottom - top));
        scroll = Math.max(0f, Math.min(scroll, maxScroll));
        if (maxScroll > 0f) {
            float track = bottom - top;
            float barW = ui.num(L + "scrollbar_width");
            float barH = Math.max(ui.num(L + "scrollbar_min"), track * track / (track + maxScroll));
            float barY = top + (track - barH) * (scroll / maxScroll);
            ui.rect(x + w - padX / 2f - barW / 2f, barY, barW, barH, barW / 2f, ui.color("stroke_12"));
        }
    }

    /**
     * Draws the rows starting at {@code y}; returns their total height. Settings grouped {@link Setting#under} a
     * parent are listed in a collapsible group below it (closed by default, state kept with the window).
     */
    private float drawRows(Ui ui, float x, float y, float w, double mx, double my) {
        float start = y;
        float rowsGap = ui.num(L + "rows_gap");
        float stroke = ui.num("stroke.width");
        List<Setting<?>> rows = new ArrayList<>();
        for (Setting<?> setting : selected.settings()) {
            if (setting.parent() == null && setting.isVisible() && controlFor(setting) != null) {
                rows.add(setting);
            }
        }
        rows.add(selected.debugLog);
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                y += rowsGap;
                ui.hline(x, y, w, ui.color("divider"));
                y += stroke + rowsGap;
            }
            Setting<?> row = rows.get(i);
            List<Setting<?>> kids = children(row);
            y += drawRow(ui, row, x, y, w, mx, my, kids.size());
            if (!kids.isEmpty()) {
                y += drawGroup(ui, row, kids, x, y, w, mx, my);
            }
        }
        return y - start;
    }

    private List<Setting<?>> children(Setting<?> parent) {
        List<Setting<?>> kids = new ArrayList<>();
        for (Setting<?> setting : selected.settings()) {
            if (setting.parent() == parent && setting.isVisible() && controlFor(setting) != null) {
                kids.add(setting);
            }
        }
        return kids;
    }

    /** Sub-rows of an open group, indented behind a guide line; the height eases open and closed. */
    private float drawGroup(Ui ui, Setting<?> parent, List<Setting<?>> kids, float x, float y, float w, double mx, double my) {
        float open = chevron(parent).open.value();
        if (open <= 0f) {
            return 0f;
        }
        float indent = ui.num(L + "group_indent");
        float rowsGap = ui.num(L + "rows_gap");
        float full = groupHeights.getOrDefault(parent, 0f);
        float shown = full <= 0f ? 0f : Math.round(full * open);
        float[] clip = currentClip();
        pushClip(ui, x - 1, Math.max(y, clip == null ? y : clip[1]), x + w + 1,
                Math.min(y + (full <= 0f ? 10_000f : shown), clip == null ? Float.MAX_VALUE : clip[3]));
        ui.pushAlpha(open);
        float cy = y + rowsGap;
        for (Setting<?> kid : kids) {
            cy += drawRow(ui, kid, x + indent, cy, w - indent, mx, my, 0) + rowsGap;
        }
        ui.popAlpha();
        popClip(ui);
        groupHeights.put(parent, cy - y);
        ui.rect(x + ui.num(L + "group_guide_x"), y + rowsGap, ui.num("stroke.width"),
                Math.max(0f, (full <= 0f ? cy - y : shown) - rowsGap * 2), 0, ui.color("stroke_10"));
        return full <= 0f ? 0f : shown;
    }

    private GroupChevron chevron(Setting<?> parent) {
        return chevrons.computeIfAbsent(parent, GroupChevron::new);
    }

    /** «3 ▾» button next to a setting that has sub-settings; opens and closes the group. */
    private final class GroupChevron extends Widget {
        private final Setting<?> parent;
        final Anim open = new Anim("expand_ms");
        int count;

        GroupChevron(Setting<?> parent) {
            this.parent = parent;
            open.snap(isOpen() ? 1f : 0f);
        }

        private String flag() {
            return "open:" + selected.id() + "." + parent.id();
        }

        boolean isOpen() {
            return frame.flag(flag());
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            float o = open.target(isOpen() ? 1f : 0f).value();
            ui.rect(x, y, w, h, h / 2f, Anim.lerpColor(ui.color("fill_04"), ui.color("fill_08"), hovered()));
            int color = Anim.lerpColor(ui.color("text_3"), ui.color("text"), Math.max(hovered(), o));
            String n = Integer.toString(count);
            float pad = ui.num(L + "group_chip_pad_x");
            ui.textCentered("menu_hint", n, x + pad, y, h, color);
            float chev = ui.num(L + "category_chevron");
            float cx = x + w - pad - chev / 2f;
            float cy = y + h / 2f;
            double a = Math.toRadians(90 * o);
            float dx = chev / 2f;
            float dy = chev / 4f;
            float[][] pts = {{-dy, -dx}, {dy, 0}, {-dy, dx}};
            float lw = ui.num("stroke.width") * 1.5f;
            float[] prev = null;
            for (float[] p : pts) {
                float px = cx + (float) (p[0] * Math.cos(a) - p[1] * Math.sin(a));
                float py = cy + (float) (p[0] * Math.sin(a) + p[1] * Math.cos(a));
                if (prev != null) {
                    ui.line(prev[0], prev[1], px, py, lw, color);
                }
                prev = new float[]{px, py};
            }
        }

        @Override
        public float preferredWidth(Ui ui) {
            return ui.num(L + "group_chip_pad_x") * 2 + ui.textWidth("menu_hint", Integer.toString(count))
                    + ui.num(L + "group_chip_gap") + ui.num(L + "category_chevron");
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                frame.setFlag(flag(), !isOpen());
                return true;
            }
            return false;
        }
    }

    private float drawRow(Ui ui, Setting<?> setting, float x, float y, float w, double mx, double my, int kids) {
        Widget control = controlFor(setting);
        float gap = ui.num(L + "row_gap");
        float controlW = controlWidth(ui, setting, control);
        float controlH = controlHeight(ui, setting, control);
        GroupChevron chevron = kids > 0 ? chevron(setting) : null;
        float chevronW = 0f;
        if (chevron != null) {
            chevron.count = kids;
            chevronW = chevron.preferredWidth(ui) + gap;
        }
        float textW = w - controlW - gap - chevronW;

        boolean debug = setting == selected.debugLog;
        String title = debug ? Ui.tr("skirmish.menu.debug_log") : Texts.settingName(setting).getString();
        Component tip = debug ? Component.translatable("skirmish.menu.debug_log.tooltip") : Texts.settingTooltip(setting);
        List<String> titleLines = ui.wrap("menu_row_title", title, textW);
        List<String> desc = tip == null ? List.of() : ui.wrap("menu_row_desc", tip.getString(), textW);
        float textH = ui.lineHeight("menu_row_title") * titleLines.size();
        if (!desc.isEmpty()) {
            textH += ui.num(L + "row_text_gap") + ui.lineHeight("menu_row_desc") * desc.size();
        }
        float h = Math.max(ui.num(L + "row_min_height"), Math.max(textH, controlH));

        float ty = y + (h - textH) / 2f;
        for (String line : titleLines) {
            ui.text("menu_row_title", line, x, ty);
            ty += ui.lineHeight("menu_row_title");
        }
        ty += ui.num(L + "row_text_gap");
        for (String line : desc) {
            ui.text("menu_row_desc", line, x, ty);
            ty += ui.lineHeight("menu_row_desc");
        }

        float cx = x + w - controlW;
        float cy = y + (h - controlH) / 2f;
        if (setting instanceof NumberSetting number) {
            control.bounds(cx, cy, ui.num(L + "slider_width"), controlH);
            widget(ui, control, mx, my);
            String value = Texts.number(number).getString();
            ui.textCentered("menu_value", value, x + w - ui.textWidth("menu_value", value), y, h);
        } else {
            control.bounds(cx, cy, controlW, controlH);
            widget(ui, control, mx, my);
        }
        if (chevron != null) {
            float ch = ui.num(L + "group_chip_height");
            chevron.bounds(cx - gap - chevron.preferredWidth(ui), y + (h - ch) / 2f, chevron.preferredWidth(ui), ch);
            widget(ui, chevron, mx, my);
        }
        return h;
    }

    private float controlWidth(Ui ui, Setting<?> setting, Widget control) {
        if (setting instanceof NumberSetting) {
            return ui.num(L + "slider_width") + ui.num(L + "row_gap") + ui.num(L + "value_width");
        }
        if (control instanceof Toggle) {
            return ui.num(L + "toggle_width");
        }
        if (control instanceof TextField) {
            return ui.num(L + "input_width");
        }
        return control.preferredWidth(ui);
    }

    private float controlHeight(Ui ui, Setting<?> setting, Widget control) {
        if (setting instanceof NumberSetting) {
            return ui.num(L + "slider_thumb");
        }
        if (control instanceof Toggle) {
            return ui.num(L + "toggle_height");
        }
        if (control instanceof Segmented segmented) {
            return segmented.preferredHeight(ui);
        }
        return ui.num(L + "keybind_height");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Widget controlFor(Setting<?> setting) {
        Widget existing = controls.get(setting);
        if (existing != null) {
            return existing;
        }
        Widget created;
        if (setting instanceof BoolSetting bool) {
            created = new Toggle(bool::get, bool::set);
        } else if (setting instanceof NumberSetting number) {
            created = new Slider(number, () -> { });
        } else if (setting instanceof EnumSetting enumSetting) {
            created = enumControl(enumSetting);
        } else if (setting instanceof StringSetting string) {
            created = new TextField(string, () -> { });
        } else if (setting instanceof ActionSetting action) {
            String key = action.translationKey() + ".button";
            created = new Button(() -> Texts.has(key) ? Ui.tr(key) : Ui.tr("skirmish.menu.run"), false, action::run);
            ((Button) created).layout(L + "small_");
        } else if (setting instanceof KeySetting key) {
            KeyMapping mapping = KeyMapping.get(key.mappingName());
            if (mapping == null) {
                return null;
            }
            created = new KeybindButton(mapping);
        } else {
            return null;
        }
        controls.put(setting, created);
        return created;
    }

    private <E extends Enum<E>> Segmented enumControl(EnumSetting<E> setting) {
        return new Segmented(Segmented.Spec.MENU,
                () -> setting.visibleValues().stream().map(v -> Texts.enumValue(setting, v).getString()).toList(),
                () -> setting.visibleValues().indexOf(setting.get()),
                i -> setting.set(setting.visibleValues().get(i)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double mx = Ui.toDesign(mouseX);
        double my = Ui.toDesign(mouseY);
        if (mx >= sideArea[0] && mx < sideArea[2] && my >= sideArea[1] && my < sideArea[3]) {
            if (sideMaxScroll > 0f) {
                sideScroll = Math.max(0f, Math.min(sideMaxScroll, sideScroll - (float) scrollY * Theme.get().num(L + "scroll_step")));
                return true;
            }
            return false;
        }
        if (maxScroll > 0f) {
            scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) scrollY * Theme.get().num(L + "scroll_step")));
            return true;
        }
        return false;
    }
}
