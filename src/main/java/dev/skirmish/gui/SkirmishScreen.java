package dev.skirmish.gui;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.hud.HudEditScreen;
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
import dev.skirmish.waypoint.WaypointsModule;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Main menu: a movable, resizable window ({@link WindowFrame}, 860×520 by default). The left rail holds the
 * category tabs and shortcuts (HUD layout, waypoints). The main area shows either the selected category's modules
 * as cards (search looks through every module and its settings) or, after a card is clicked, that module's page:
 * a header with the module switch and the settings built from its {@link Setting}s — Bool → toggle, Number →
 * slider, Enum → segmented, String → text field, Action → button, Key → keybind button; sub-settings grouped
 * {@link Setting#under} a parent open as a dropdown. Esc, Backspace or the mouse back button return to the cards;
 * the view slides between pages. The last category, module and page are remembered while the game runs.
 */
public final class SkirmishScreen extends UiScreen {
    private static final String L = "layout.menu.";
    private static String lastSelected = "";
    private static @Nullable Category lastCategory;
    private static boolean lastOnModule;

    private enum View {
        CARDS, MODULE
    }

    private final List<Module> modules;
    private final WindowFrame frame = new WindowFrame("menu", L);
    private final Map<Category, CategoryTab> tabs = new EnumMap<>(Category.class);
    private final Map<Module, ModuleCard> cards = new IdentityHashMap<>();
    private final List<Shortcut> shortcuts = new ArrayList<>();
    private final LogSwitch logSwitch = new LogSwitch();
    private final Map<Setting<?>, Widget> controls = new IdentityHashMap<>();
    private final Map<Setting<?>, GroupChevron> chevrons = new IdentityHashMap<>();
    /** Height of each open group's sub-rows as last drawn, for the open/close animation. */
    private final Map<Setting<?>, Float> groupHeights = new IdentityHashMap<>();
    private final StringSetting query = new StringSetting("search", "", 40, false);
    private final TextField search;
    private final IconButton clearSearch;
    private final BackButton back = new BackButton();
    private final IconButton prev;
    private final IconButton next;
    private final Toggle moduleToggle;
    private final Button reset;
    private final Button done;
    /** Page transition: 0 → 1 after each change of view, category or module. */
    private final Anim page = new Anim("page_ms");
    private float pageDir = 1f;
    private long openedAt;
    private Category category;
    private Module selected;
    private View view;
    private float gridScroll;
    private float gridMax;
    private float rowsScroll;
    private float rowsMax;

    public SkirmishScreen(@Nullable Screen parent) {
        super(Component.translatable("skirmish.menu.title"), parent);
        List<Module> all = new ArrayList<>(ModuleManager.get().all());
        all.sort(Comparator.comparingInt(Module::menuOrder));
        this.modules = List.copyOf(all);
        this.selected = modules.stream().filter(m -> m.id().equals(lastSelected)).findFirst().orElse(modules.getFirst());
        this.view = lastOnModule ? View.MODULE : View.CARDS;
        this.category = view == View.MODULE || lastCategory == null ? selected.category() : lastCategory;
        for (Module module : modules) {
            cards.put(module, new ModuleCard(module));
        }
        for (Category c : Category.values()) {
            tabs.put(c, new CategoryTab(c));
        }
        shortcuts.add(new Shortcut("skirmish.menu.shortcut.hud", CategoryIcons::layout,
                () -> minecraft.setScreen(new HudEditScreen(this)), () -> true));
        shortcuts.add(new Shortcut("skirmish.menu.shortcut.waypoints", CategoryIcons::pin,
                () -> minecraft.setScreen(new WaypointListScreen(this)), () -> moduleVisible(WaypointsModule.ID)));
        this.search = new TextField(query, () -> gridScroll = 0f).placeholder(() -> Ui.tr("skirmish.menu.search"));
        this.clearSearch = new IconButton(IconButton.CLEAR, () -> {
            query.set("");
            gridScroll = 0f;
        });
        this.prev = new IconButton(IconButton.LEFT, () -> step(-1));
        this.next = new IconButton(IconButton.RIGHT, () -> step(1));
        this.moduleToggle = new Toggle(() -> selected.isEnabled(), value -> selected.setEnabled(value));
        this.reset = new Button(() -> Ui.tr("skirmish.menu.reset"), false, this::resetModule);
        this.done = new Button(() -> Ui.tr("skirmish.menu.done"), true, this::onClose);
        page.snap(1f);
    }

    /** Opens the menu on a given module's page (e.g. from the HUD editor). */
    public static void selectNext(String moduleId) {
        lastSelected = moduleId;
        lastOnModule = true;
    }

    @Override
    protected void init() {
        super.init();
        openedAt = Util.getMillis();
    }

    // ---- navigation ----

    private void go(View to, float direction) {
        view = to;
        lastOnModule = to == View.MODULE;
        lastCategory = category;
        pageDir = direction;
        page.snap(0f);
        page.target(1f);
    }

    private void openModule(Module module) {
        showModule(module);
        category = module.category();
        go(View.MODULE, 1f);
    }

    private void showModule(Module module) {
        if (module != selected) {
            selected = module;
            controls.clear();
            chevrons.clear();
            groupHeights.clear();
        }
        rowsScroll = 0f;
        lastSelected = module.id();
    }

    private void backToCards() {
        if (view == View.MODULE) {
            go(View.CARDS, -1f);
        }
    }

    private void openCategory(Category c) {
        boolean searching = searching();
        if (c == category && view == View.CARDS && !searching) {
            return;
        }
        float direction = c == category ? -1f : c.ordinal() > category.ordinal() ? 1f : -1f;
        query.set("");
        category = c;
        gridScroll = 0f;
        go(View.CARDS, direction);
    }

    /** Previous / next module of the open module's category, wrapping around. */
    private void step(int delta) {
        List<Module> members = membersOf(selected.category());
        if (members.size() < 2) {
            return;
        }
        int i = Math.max(0, members.indexOf(selected));
        showModule(members.get(Math.floorMod(i + delta, members.size())));
        go(View.MODULE, delta);
    }

    private boolean searching() {
        return !query.get().isBlank();
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

    // ---- module lists ----

    /** Modules not blocked by Feature Control (blocked ones must not even be mentioned). */
    private List<Module> visibleModules() {
        return modules.stream().filter(m -> !m.isBlocked()).toList();
    }

    private List<Module> membersOf(Category c) {
        return modules.stream().filter(m -> !m.isBlocked() && m.category() == c).toList();
    }

    private boolean moduleVisible(String id) {
        return modules.stream().anyMatch(m -> m.id().equals(id) && !m.isBlocked());
    }

    private List<Category> visibleCategories() {
        List<Category> out = new ArrayList<>();
        for (Category c : Category.values()) {
            if (!membersOf(c).isEmpty()) {
                out.add(c);
            }
        }
        return out;
    }

    private static String name(Module module) {
        return Texts.tr(module.nameKey(), Texts.humanize(module.id())).getString();
    }

    private static String description(Module module) {
        return Texts.tr(module.descriptionKey(), "").getString();
    }

    /**
     * Cards to show: the category's modules, or every module whose name, description or one of whose settings
     * matches the search (the card then names the matching setting).
     */
    private List<Module> shownModules() {
        String q = query.get().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return membersOf(category);
        }
        List<Module> out = new ArrayList<>();
        for (Module module : visibleModules()) {
            ModuleCard card = cards.get(module);
            card.match = null;
            if (name(module).toLowerCase(Locale.ROOT).contains(q) || description(module).toLowerCase(Locale.ROOT).contains(q)
                    || module.id().contains(q)) {
                out.add(module);
                continue;
            }
            for (Setting<?> setting : module.settings()) {
                String title = Texts.settingName(setting).getString();
                if (!setting.isBlocked() && title.toLowerCase(Locale.ROOT).contains(q)) {
                    card.match = title;
                    out.add(module);
                    break;
                }
            }
        }
        return out;
    }

    // ---- frame ----

    @Override
    protected void draw(Ui ui, double mx, double my) {
        if (view == View.MODULE && selected.isBlocked()) {
            view = View.CARDS;
            lastOnModule = false;
        }
        List<Category> categories = visibleCategories();
        if (!categories.isEmpty() && !categories.contains(category)) {
            category = categories.getFirst();
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

        float rail = ui.num(L + "sidebar_width");
        drawRail(ui, x, y, ch, categories, mx, my);

        float mainX = x + rail;
        float mainW = cw - rail;
        float p = page.value();
        float dx = Math.round((1f - p) * ui.num(L + "page_slide") * pageDir);
        pushClip(ui, mainX, y, mainX + mainW, y + ch);
        ui.pushAlpha(p);
        if (view == View.CARDS) {
            drawCards(ui, mainX + dx, y, mainW, ch, mx, my);
        } else {
            drawModule(ui, mainX + dx, y, mainW, ch, mx, my);
        }
        ui.popAlpha();
        popClip(ui);
        widget(ui, frame.grip, mx, my);
    }

    // ---- rail ----

    private void drawRail(Ui ui, float x, float y, float h, List<Category> categories, double mx, double my) {
        float sw = ui.num(L + "sidebar_width");
        float stroke = ui.num("stroke.width");
        float inner = ui.theme().radius("window") - stroke;
        // Left corners follow the window's rounding: a wider rounded rect clipped at the rail edge.
        pushClip(ui, x, y, x + sw, y + h);
        ui.rect(x, y, sw + inner * 2, h, inner, ui.color("sidebar"));
        ui.rect(x + sw - stroke, y, stroke, h, 0, ui.color("stroke"));

        float padX = ui.num(L + "sidebar_pad_x");
        float padY = ui.num(L + "sidebar_pad_y");
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
        ModuleIcons.logo(ui, bx, ly, logo, ui.color("white"), ui.color("logo_slash"));
        float tx = bx + logo + ui.num(L + "brand_gap");
        float ty = by + (rowH - textH) / 2f;
        ui.text("menu_brand", "Skirmish", tx, ty);
        ui.text("menu_brand_sub", Ui.tr("skirmish.menu.brand_sub", SharedConstants.getCurrentVersion().name()), tx, ty + ui.lineHeight("menu_brand"));
        cy = by + rowH + ui.num(L + "brand_pad_bottom");

        ui.text("menu_section", Ui.tr("skirmish.menu.categories"), cx + ui.num(L + "section_pad_x"), cy);
        cy += ui.lineHeight("menu_section") + ui.num(L + "section_pad_bottom");
        float tabH = ui.num(L + "tab_height");
        float tabGap = ui.num(L + "tab_gap");
        for (Category c : categories) {
            CategoryTab tab = tabs.get(c);
            tab.bounds(cx, cy, cwid, tabH);
            widget(ui, tab, mx, my);
            cy += tabH + tabGap;
        }
        cy += ui.num(L + "rail_divider_gap") - tabGap;
        ui.hline(cx + ui.num(L + "section_pad_x"), cy, cwid - ui.num(L + "section_pad_x") * 2, ui.color("divider"));
        cy += stroke + ui.num(L + "rail_divider_gap");
        float shortcutH = ui.num(L + "shortcut_height");
        for (Shortcut shortcut : shortcuts) {
            if (shortcut.visible.getAsBoolean()) {
                shortcut.bounds(cx, cy, cwid, shortcutH);
                widget(ui, shortcut, mx, my);
                cy += shortcutH + tabGap;
            }
        }
        logSwitch.bounds(cx, cy, cwid, shortcutH);
        widget(ui, logSwitch, mx, my);
        cy += shortcutH + tabGap;

        // Footer hint (bottom aligned, only when there is room): key chip + "open menu".
        float hintPad = ui.num(L + "hint_pad");
        float chipPadX = ui.num(L + "hint_key_pad_x");
        float chipPadY = ui.num(L + "hint_key_pad_y");
        String key = SkirmishKeys.OPEN_MENU.isUnbound() ? Ui.tr("skirmish.ui.keybind_none") : KeyNames.shortName(SkirmishKeys.OPEN_MENU);
        float chipH = ui.lineHeight("menu_hint_key") + chipPadY * 2 + stroke * 2;
        float hintH = Math.max(chipH, ui.lineHeight("menu_hint"));
        float hy = y + h - padY - hintPad - hintH;
        if (hy >= cy + hintPad) {
            float chipW = ui.textWidth("menu_hint_key", key) + chipPadX * 2 + stroke * 2;
            float hx = cx + hintPad;
            float chipY = hy + (hintH - chipH) / 2f;
            ui.border(hx, chipY, chipW, chipH, ui.theme().radius("chip"), stroke, ui.color("stroke_10"));
            ui.text("menu_hint_key", key, hx + stroke + chipPadX, chipY + stroke + chipPadY);
            ui.textCentered("menu_hint", Ui.tr("skirmish.menu.hint_open"), hx + chipW + ui.num(L + "hint_gap"), hy, hintH);
        }
        popClip(ui);
    }

    /** Category tab: icon, name and «enabled/total»; the selected one is tinted with an accent bar. */
    private final class CategoryTab extends Widget {
        private final Category category;
        private final Anim active = new Anim("hover_ms");

        CategoryTab(Category category) {
            this.category = category;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            boolean selectedTab = category == SkirmishScreen.this.category && !searching();
            float a = active.target(selectedTab).value();
            float hov = hovered();
            int idle = Anim.lerpColor(ui.color("fill_00"), ui.color("fill_04"), hov);
            ui.rect(x, y, w, h, ui.theme().radius("button"), Anim.lerpColor(idle, ui.color("accent_16"), a));
            if (a > 0f) {
                float bar = ui.num(L + "tab_bar");
                float barH = h * 0.5f * a;
                ui.rect(x, y + (h - barH) / 2f, bar, barH, bar / 2f, ui.color("accent"));
            }
            float padX = ui.num(L + "tab_pad_x");
            float icon = ui.num(L + "tab_icon");
            int iconColor = Anim.lerpColor(Anim.lerpColor(ui.color("text_3"), ui.color("text_2"), hov), ui.color("accent"), a);
            CategoryIcons.draw(ui, category, x + padX, y + (h - icon) / 2f, icon, iconColor);

            List<Module> members = membersOf(category);
            long on = members.stream().filter(Module::isEnabled).count();
            String count = on + "/" + members.size();
            float countPad = ui.num(L + "tab_count_pad_x");
            float countH = ui.num(L + "tab_count_height");
            float countW = ui.textWidth("menu_tab_count", count) + countPad * 2;
            float countX = x + w - padX - countW;
            ui.rect(countX, y + (h - countH) / 2f, countW, countH, countH / 2f,
                    Anim.lerpColor(ui.color("fill_05"), ui.color("accent_18"), a));
            ui.textCentered("menu_tab_count", count, countX + countPad, y, h,
                    Anim.lerpColor(ui.color("text_3"), ui.color("text"), a));

            float tx = x + padX + icon + ui.num(L + "tab_icon_gap");
            int color = Anim.lerpColor(Anim.lerpColor(ui.color("text_2"), ui.color("text"), hov), ui.color("white"), a);
            String label = ui.ellipsize("menu_tab", Ui.tr(category.translationKey()), countX - tx - padX / 2f);
            ui.textCentered("menu_tab", label, tx, y, h, color);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                openCategory(category);
                return true;
            }
            return false;
        }
    }

    /**
     * «Debug Log» for every module at once: on when all modules log; a click turns them all on, or all off when they
     * all were on. Shows how many log while only some do.
     */
    private final class LogSwitch extends Widget {
        private final Anim on = new Anim("toggle_ms");

        private long logging() {
            return modules.stream().filter(m -> m.debugLog.get()).count();
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            float hov = hovered();
            long count = logging();
            boolean all = count == modules.size();
            float o = on.target(all).value();
            ui.rect(x, y, w, h, ui.theme().radius("button"), Anim.lerpColor(ui.color("fill_00"), ui.color("fill_04"), hov));
            float padX = ui.num(L + "tab_pad_x");
            float size = ui.num(L + "tab_icon");
            int iconColor = Anim.lerpColor(Anim.lerpColor(ui.color("text_3"), ui.color("text_2"), hov), ui.color("accent"), o);
            CategoryIcons.log(ui, x + padX, y + (h - size) / 2f, size, iconColor);
            float tx = x + padX + size + ui.num(L + "tab_icon_gap");

            // Mini switch on the right.
            float sw = ui.num(L + "log_switch_width");
            float sh = ui.num(L + "log_switch_height");
            float sx = x + w - padX - sw;
            float sy = y + (h - sh) / 2f;
            ui.rect(sx, sy, sw, sh, sh / 2f, Anim.lerpColor(ui.color("toggle_off"), ui.color("accent"), o));
            float knob = sh - ui.num(L + "log_switch_pad") * 2;
            float kx = sx + ui.num(L + "log_switch_pad") + (sw - sh) * o;
            ui.circle(kx + knob / 2f, sy + sh / 2f, knob, ui.color("white"));

            String label = Ui.tr("skirmish.menu.debug_all");
            String counter = count > 0 && !all ? count + "/" + modules.size() : "";
            float counterW = counter.isEmpty() ? 0f : ui.textWidth("menu_tab_count", counter) + ui.num(L + "tab_icon_gap");
            label = ui.ellipsize("menu_shortcut", label, sx - ui.num(L + "tab_icon_gap") - counterW - tx);
            float after = ui.textCentered("menu_shortcut", label, tx, y, h, Anim.lerpColor(ui.color("text_3"), ui.color("text"), Math.max(hov, o)));
            if (!counter.isEmpty()) {
                ui.textCentered("menu_tab_count", counter, after + ui.num(L + "tab_icon_gap") / 2f, y, h, ui.color("accent"));
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0) {
                return false;
            }
            boolean turnOn = logging() < modules.size();
            for (Module module : modules) {
                module.debugLog.set(turnOn);
            }
            ModuleManager.get().markDirty();
            return true;
        }
    }

    @FunctionalInterface
    private interface IconPainter {
        void paint(Ui ui, float x, float y, float size, int color);
    }

    /** Rail link to another screen (HUD layout, waypoints). */
    private final class Shortcut extends Widget {
        private final String key;
        private final IconPainter icon;
        private final Runnable action;
        final BooleanSupplier visible;

        Shortcut(String key, IconPainter icon, Runnable action, BooleanSupplier visible) {
            this.key = key;
            this.icon = icon;
            this.action = action;
            this.visible = visible;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            float hov = hovered();
            ui.rect(x, y, w, h, ui.theme().radius("button"), Anim.lerpColor(ui.color("fill_00"), ui.color("fill_04"), hov));
            float padX = ui.num(L + "tab_pad_x");
            float size = ui.num(L + "tab_icon");
            icon.paint(ui, x + padX, y + (h - size) / 2f, size, Anim.lerpColor(ui.color("text_3"), ui.color("text_2"), hov));
            float tx = x + padX + size + ui.num(L + "tab_icon_gap");
            float chev = ui.num(L + "card_chevron");
            String label = ui.ellipsize("menu_shortcut", Ui.tr(key), x + w - padX - chev - tx);
            ui.textCentered("menu_shortcut", label, tx, y, h, Anim.lerpColor(ui.color("text_3"), ui.color("text"), hov));
            CategoryIcons.chevron(ui, x + w - padX - chev + hov * 2f, y + (h - chev) / 2f, chev,
                    Anim.lerpColor(ui.color("fill_00"), ui.color("text_3"), hov), false);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                action.run();
                return true;
            }
            return false;
        }
    }

    // ---- cards page ----

    private void drawCards(Ui ui, float x, float y, float w, float h, double mx, double my) {
        float padX = ui.num(L + "content_pad_x");
        float padY = ui.num(L + "content_pad_y");
        float gap = ui.num(L + "content_gap");
        float stroke = ui.num("stroke.width");
        float cx = x + padX;
        float cw = w - padX * 2;
        float cy = y + padY;
        boolean searching = searching();
        List<Module> list = shownModules();

        // Header: category tile, title and counts; search on the right.
        float tile = ui.num(L + "header_tile");
        float searchW = Math.min(ui.num(L + "search_width"), cw * 0.45f);
        float searchH = ui.num(L + "keybind_height");
        float textH = ui.lineHeight("menu_title") + ui.num(L + "header_text_gap") + ui.lineHeight("menu_desc");
        float headH = Math.max(tile, Math.max(textH, searchH));
        float tileY = cy + (headH - tile) / 2f;
        ui.rect(cx, tileY, tile, tile, ui.theme().radius("tile"), ui.color("accent_16"));
        float icon = ui.num(L + "header_icon");
        if (searching) {
            CategoryIcons.search(ui, cx + (tile - icon) / 2f, tileY + (tile - icon) / 2f, icon, ui.color("accent"));
        } else {
            CategoryIcons.draw(ui, category, cx + (tile - icon) / 2f, tileY + (tile - icon) / 2f, icon, ui.color("accent"));
        }
        float tx = cx + tile + ui.num(L + "header_tile_gap");
        float textW = cx + cw - searchW - ui.num(L + "header_gap") - tx;
        float ty = cy + (headH - textH) / 2f;
        String title = searching ? Ui.tr("skirmish.menu.search_title") : Ui.tr(category.translationKey());
        ui.text("menu_title", ui.ellipsize("menu_title", title, textW), tx, ty);
        long on = list.stream().filter(Module::isEnabled).count();
        String sub = searching
                ? list.size() + " " + Ui.plural("skirmish.menu.count.found", list.size())
                : list.size() + " " + Ui.plural("skirmish.menu.count.module", list.size()) + " · " + Ui.tr("skirmish.menu.count.enabled", on);
        ui.text("menu_desc", ui.ellipsize("menu_desc", sub, textW), tx, ty + ui.lineHeight("menu_title") + ui.num(L + "header_text_gap"));

        search.bounds(cx + cw - searchW, cy + (headH - searchH) / 2f, searchW, searchH);
        widget(ui, search, mx, my);
        float si = ui.num(L + "search_icon");
        float sx = search.x + search.w - ui.num(L + "keybind_pad_x") - si;
        if (query.get().isEmpty()) {
            if (!search.isFocused()) {
                CategoryIcons.search(ui, sx, search.y + (searchH - si) / 2f, si, ui.color("text_3"));
            }
        } else {
            float hit = searchH - 4;
            clearSearch.bounds(search.x + search.w - hit - 2, search.y + 2, hit, hit);
            widget(ui, clearSearch, mx, my);
        }
        cy += headH + gap;
        ui.hline(cx, cy, cw, ui.color("stroke"));
        cy += stroke;

        // Card grid, scrollable below the divider.
        float top = cy;
        float bottom = y + h - stroke;
        float cardGap = ui.num(L + "card_gap");
        int cols = Math.max(1, (int) ((cw + cardGap) / (ui.num(L + "card_min_width") + cardGap)));
        float cardW = (cw - cardGap * (cols - 1)) / cols;
        float cardH = ui.num(L + "card_height");
        int rows = (list.size() + cols - 1) / cols;
        float content = gap * 2 + rows * cardH + Math.max(0, rows - 1) * cardGap;
        pushClip(ui, x, top, x + w, bottom);
        float start = top + gap - gridScroll;
        for (int i = 0; i < list.size(); i++) {
            float cardX = Math.round(cx + (i % cols) * (cardW + cardGap));
            float cardY = Math.round(start + (i / cols) * (cardH + cardGap));
            if (cardY + cardH < top || cardY > bottom) {
                continue;
            }
            ModuleCard card = cards.get(list.get(i));
            card.searching = searching;
            card.bounds(cardX, cardY, Math.round(cx + (i % cols) * (cardW + cardGap) + cardW) - cardX, cardH);
            widget(ui, card, mx, my);
            card.placeToggle(ui);
            widget(ui, card.toggle, mx, my);
        }
        if (list.isEmpty()) {
            String empty = Ui.tr(searching ? "skirmish.menu.search_empty" : "skirmish.menu.category_empty");
            float ew = ui.textWidth("menu_empty", empty);
            ui.text("menu_empty", empty, cx + (cw - ew) / 2f, top + ui.num(L + "empty_pad"));
        }
        popClip(ui);
        gridMax = Math.max(0f, content - (bottom - top));
        gridScroll = Math.max(0f, Math.min(gridScroll, gridMax));
        scrollbar(ui, x + w - padX / 2f, top, bottom, gridScroll, gridMax);
    }

    private static void scrollbar(Ui ui, float centerX, float top, float bottom, float scroll, float max) {
        if (max <= 0f) {
            return;
        }
        float track = bottom - top;
        float barW = ui.num(L + "scrollbar_width");
        float barH = Math.max(ui.num(L + "scrollbar_min"), track * track / (track + max));
        float barY = top + (track - barH) * (scroll / max);
        ui.rect(centerX - barW / 2f, barY, barW, barH, barW / 2f, ui.color("stroke_12"));
    }

    /** Icon tile of a module, tinted when the module is on ({@code on} 0..1). */
    private static void moduleTile(Ui ui, Module module, float x, float y, float size, float radius, float on) {
        ui.rect(x, y, size, size, radius, Anim.lerpColor(ui.color("fill_05"), ui.color("accent_16"), on));
        float icon = Math.round(size * ui.num(L + "tile_icon_ratio"));
        ModuleIcons.draw(ui, module, x + (size - icon) / 2f, y + (size - icon) / 2f, icon,
                Anim.lerpColor(ui.color("text_3"), ui.color("accent"), on));
    }

    /**
     * Module card: icon, name, switch, two lines of description, the settings count (or the setting that
     * matched the search), its bound key and an arrow. Clicking anywhere but the switch opens the module page.
     */
    private final class ModuleCard extends Widget {
        private final Module module;
        private final Anim on = new Anim("toggle_ms");
        final Toggle toggle;
        @Nullable String match;
        boolean searching;

        ModuleCard(Module module) {
            this.module = module;
            this.toggle = new Toggle(module::isEnabled, module::setEnabled);
        }

        void placeToggle(Ui ui) {
            float pad = ui.num(L + "card_pad");
            float mono = ui.num(L + "card_mono");
            toggle.enabled = module.canToggle();
            toggle.at(x + w - pad - ui.num(L + "toggle_width"), y + pad + (mono - ui.num(L + "toggle_height")) / 2f);
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            float hov = hovered();
            float o = on.target(module.isEnabled()).value();
            int border = Anim.lerpColor(Anim.lerpColor(ui.color("stroke_07"), ui.color("stroke_12"), hov),
                    Anim.lerpColor(ui.color("accent_18"), ui.color("accent_60"), hov), o);
            ui.box(x, y, w, h, ui.theme().radius("tile"), Anim.lerpColor(ui.color("fill_04"), ui.color("fill_06"), hov), border);

            float pad = ui.num(L + "card_pad");
            float mono = ui.num(L + "card_mono");
            moduleTile(ui, module, x + pad, y + pad, mono, ui.theme().radius("button"), o);
            float tx = x + pad + mono + ui.num(L + "card_mono_gap");
            float nameW = x + w - pad - ui.num(L + "toggle_width") - ui.num(L + "card_mono_gap") - tx;
            String title = ui.ellipsize("menu_card_title", name(module), nameW);
            if (searching) {
                float lines = ui.lineHeight("menu_card_title") + ui.lineHeight("menu_card_meta");
                float ty = y + pad + (mono - lines) / 2f;
                ui.text("menu_card_title", title, tx, ty);
                ui.text("menu_card_meta", ui.ellipsize("menu_card_meta", Ui.tr(module.category().translationKey()), nameW),
                        tx, ty + ui.lineHeight("menu_card_title"));
            } else {
                ui.textCentered("menu_card_title", title, tx, y + pad, mono);
            }

            // Description: at most the lines that fit above the footer, the last one ellipsized.
            float dy = y + pad + mono + ui.num(L + "card_desc_gap");
            float metaH = ui.lineHeight("menu_card_meta");
            float footY = y + h - pad - metaH;
            float lineH = ui.lineHeight("menu_card_desc");
            int maxLines = Math.max(0, (int) ((footY - ui.num(L + "card_meta_gap") - dy) / lineH));
            List<String> lines = ui.wrap("menu_card_desc", description(module), w - pad * 2);
            for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
                String line = lines.get(i);
                if (i == maxLines - 1 && lines.size() > maxLines) {
                    line = ui.ellipsize("menu_card_desc", line + "…", w - pad * 2);
                }
                ui.text("menu_card_desc", line, x + pad, dy + i * lineH);
            }

            // Footer: settings count or the matching setting, key chip, arrow.
            float chev = ui.num(L + "card_chevron");
            float fx = x + pad;
            float fw = w - pad * 2 - chev - ui.num(L + "card_meta_gap");
            String keyName = boundKey();
            float keyW = 0f;
            float keyPadX = ui.num(L + "card_key_pad_x");
            float keyPadY = ui.num(L + "card_key_pad_y");
            if (keyName != null) {
                keyW = ui.textWidth("menu_hint_key", keyName) + keyPadX * 2;
            }
            String meta = match != null ? Ui.tr("skirmish.menu.card.match", match) : settingsLabel();
            float metaW = fw - (keyW > 0 ? keyW + ui.num(L + "card_meta_gap") : 0f);
            String metaText = ui.ellipsize("menu_card_meta", meta, metaW);
            float after = ui.text("menu_card_meta", metaText, fx, footY, match != null ? ui.color("accent") : ui.color("text_3"));
            if (keyName != null) {
                float kh = ui.lineHeight("menu_hint_key") + keyPadY * 2;
                float kx = metaText.isEmpty() ? fx : after + ui.num(L + "card_meta_gap");
                float ky = footY + (metaH - kh) / 2f;
                ui.border(kx, ky, keyW, kh, ui.theme().radius("chip"), ui.num("stroke.width"), ui.color("stroke_12"));
                ui.text("menu_hint_key", keyName, kx + keyPadX, ky + keyPadY);
            }
            CategoryIcons.chevron(ui, x + w - pad - chev + hov * 3f, footY + (metaH - chev) / 2f, chev,
                    Anim.lerpColor(ui.color("text_3"), ui.color("text"), hov), false);
        }

        private String settingsLabel() {
            int n = 0;
            for (Setting<?> setting : module.settings()) {
                boolean shown = setting instanceof BoolSetting || setting instanceof NumberSetting || setting instanceof EnumSetting
                        || setting instanceof StringSetting || setting instanceof ActionSetting;
                if (!setting.isBlocked() && shown) {
                    n++;
                }
            }
            return n == 0 ? Ui.tr("skirmish.menu.card.no_settings") : n + " " + Ui.plural("skirmish.menu.count.setting", n);
        }

        private @Nullable String boundKey() {
            for (Setting<?> setting : module.settings()) {
                if (setting instanceof KeySetting key && !setting.isBlocked()) {
                    KeyMapping mapping = KeyMapping.get(key.mappingName());
                    if (mapping != null && !mapping.isUnbound()) {
                        return KeyNames.shortName(mapping);
                    }
                }
            }
            return null;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                openModule(module);
                return true;
            }
            if (button == 1 && module.canToggle()) {
                module.toggle();
                return true;
            }
            return false;
        }
    }

    // ---- module page ----

    private void drawModule(Ui ui, float x, float y, float w, float h, double mx, double my) {
        float padX = ui.num(L + "content_pad_x");
        float padY = ui.num(L + "content_pad_y");
        float gap = ui.num(L + "content_gap");
        float cx = x + padX;
        float cw = w - padX * 2;
        float cy = y + padY;

        // Top bar: back to the cards, previous / next module of the category.
        float bh = ui.num(L + "back_height");
        back.label = searching() ? Ui.tr("skirmish.menu.search_title") : Ui.tr(category.translationKey());
        back.bounds(cx, cy, back.preferredWidth(ui), bh);
        widget(ui, back, mx, my);
        List<Module> members = membersOf(selected.category());
        if (members.size() > 1) {
            String pos = (members.indexOf(selected) + 1) + " / " + members.size();
            float pw = ui.textWidth("menu_hint", pos);
            float stepGap = ui.num(L + "step_gap");
            next.bounds(cx + cw - bh, cy, bh, bh);
            float posX = next.x - stepGap - pw;
            ui.textCentered("menu_hint", pos, posX, cy, bh);
            prev.bounds(posX - stepGap - bh, cy, bh, bh);
            widget(ui, prev, mx, my);
            widget(ui, next, mx, my);
        }
        cy += bh + ui.num(L + "topbar_gap");

        // Header: icon tile, title + description, module switch on the right.
        float tile = ui.num(L + "header_tile");
        float toggleW = ui.num(L + "toggle_width");
        float tileGap = ui.num(L + "header_tile_gap");
        float textX = cx + tile + tileGap;
        float textW = cx + cw - toggleW - ui.num(L + "header_gap") - textX;
        moduleTile(ui, selected, cx, cy, tile, ui.theme().radius("tile"), selected.isEnabled() ? 1f : 0f);
        ui.text("menu_title", ui.ellipsize("menu_title", name(selected), textW), textX, cy);
        float ty = cy + ui.lineHeight("menu_title") + ui.num(L + "header_text_gap");
        for (String line : ui.wrap("menu_desc", description(selected), textW)) {
            ui.text("menu_desc", line, textX, ty);
            ty += ui.lineHeight("menu_desc");
        }
        moduleToggle.enabled = selected.canToggle();
        moduleToggle.at(cx + cw - toggleW, cy + (tile - ui.num(L + "toggle_height")) / 2f);
        widget(ui, moduleToggle, mx, my);
        cy = Math.max(ty, cy + tile) + gap;
        ui.hline(cx, cy, cw, ui.color("stroke"));
        cy += ui.num("stroke.width");

        // Footer buttons, right aligned.
        float buttonH = done.preferredHeight(ui);
        float fy = y + h - padY - buttonH;
        float doneW = done.preferredWidth(ui);
        float resetW = reset.preferredWidth(ui);
        done.bounds(cx + cw - doneW, fy, doneW, buttonH);
        reset.bounds(done.x - ui.num(L + "footer_gap") - resetW, fy, resetW, buttonH);
        widget(ui, reset, mx, my);
        widget(ui, done, mx, my);

        // Settings rows, scrollable between the divider and the footer.
        float top = cy;
        float bottom = fy - gap;
        pushClip(ui, x, top, x + w, bottom);
        float content = drawRows(ui, cx, top + gap - rowsScroll, cw, mx, my) + gap;
        popClip(ui);
        rowsMax = Math.max(0f, content - (bottom - top));
        rowsScroll = Math.max(0f, Math.min(rowsScroll, rowsMax));
        scrollbar(ui, x + w - padX / 2f, top, bottom, rowsScroll, rowsMax);
    }

    /** «‹ Бой»: back from a module page to its category's cards. */
    private final class BackButton extends Widget {
        String label = "";

        @Override
        public float preferredWidth(Ui ui) {
            return ui.num(L + "back_pad_x") * 2 + ui.num(L + "back_icon") + ui.num(L + "back_gap") + ui.textWidth("menu_back", label);
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            float hov = hovered();
            float r = ui.theme().radius("button_sm");
            ui.rect(x, y, w, h, r, Anim.lerpColor(ui.color("fill_04"), ui.color("fill_08"), hov));
            ui.border(x, y, w, h, r, ui.num("stroke.width"), ui.color("stroke_10"));
            float icon = ui.num(L + "back_icon");
            float pad = ui.num(L + "back_pad_x");
            int color = Anim.lerpColor(ui.color("text_2"), ui.color("text"), hov);
            CategoryIcons.chevron(ui, x + pad - hov * 2f, y + (h - icon) / 2f, icon, color, true);
            ui.textCentered("menu_back", label, x + pad + icon + ui.num(L + "back_gap"), y, h, color);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                backToCards();
                return true;
            }
            return false;
        }
    }

    /** Square icon button: previous / next module, clear search. */
    private static final class IconButton extends Widget {
        static final int LEFT = 0;
        static final int RIGHT = 1;
        static final int CLEAR = 2;
        private final int kind;
        private final Runnable action;

        IconButton(int kind, Runnable action) {
            this.kind = kind;
            this.action = action;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            float hov = hovered();
            float r = ui.theme().radius("button_sm");
            if (kind == CLEAR) {
                float icon = ui.num(L + "search_icon");
                Icons.close(ui, x + (w - icon) / 2f, y + (h - icon) / 2f, icon, 2f, Anim.lerpColor(ui.color("text_3"), ui.color("text"), hov));
                return;
            }
            ui.rect(x, y, w, h, r, Anim.lerpColor(ui.color("fill_04"), ui.color("fill_08"), hov));
            ui.border(x, y, w, h, r, ui.num("stroke.width"), ui.color("stroke_10"));
            float icon = ui.num(L + "back_icon");
            CategoryIcons.chevron(ui, x + (w - icon) / 2f, y + (h - icon) / 2f, icon,
                    Anim.lerpColor(ui.color("text_2"), ui.color("text"), hov), kind == LEFT);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                action.run();
                return true;
            }
            return false;
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

    /** «3 ›» chip next to a setting that has sub-settings; opens and closes the group. */
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
            float[] prevPoint = null;
            for (float[] p : pts) {
                float px = cx + (float) (p[0] * Math.cos(a) - p[1] * Math.sin(a));
                float py = cy + (float) (p[0] * Math.sin(a) + p[1] * Math.cos(a));
                if (prevPoint != null) {
                    ui.line(prevPoint[0], prevPoint[1], px, py, lw, color);
                }
                prevPoint = new float[]{px, py};
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

    // ---- input ----

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (focusedWidget() == null) {
            boolean backKey = event.isEscape() || event.key() == GLFW.GLFW_KEY_BACKSPACE;
            if (view == View.MODULE && backKey) {
                backToCards();
                return true;
            }
            if (view == View.CARDS && event.isEscape() && searching()) {
                query.set("");
                gridScroll = 0f;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /** Typing on the cards page starts a search (ignoring the first moments, when the menu key's char arrives). */
    @Override
    public boolean charTyped(CharacterEvent event) {
        if (super.charTyped(event)) {
            return true;
        }
        if (focusedWidget() == null && view == View.CARDS && Util.getMillis() - openedAt > 250
                && !Character.isWhitespace(event.codepoint())) {
            focus(search);
            return search.charTyped(event);
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_4 && view == View.MODULE) {
            backToCards();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float step = (float) scrollY * Theme.get().num(L + "scroll_step");
        if (view == View.CARDS) {
            if (gridMax > 0f) {
                gridScroll = Math.max(0f, Math.min(gridMax, gridScroll - step));
                return true;
            }
            return false;
        }
        if (rowsMax > 0f) {
            rowsScroll = Math.max(0f, Math.min(rowsMax, rowsScroll - step));
            return true;
        }
        return false;
    }
}
