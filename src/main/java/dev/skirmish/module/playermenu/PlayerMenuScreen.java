package dev.skirmish.module.playermenu;

import dev.skirmish.module.analytics.dossier.DossierModule;
import dev.skirmish.module.friends.Friends;
import dev.skirmish.setting.StringSetting;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.TextField;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The player menu: a compact panel in the middle of the screen. With a target: the nick, the dossier line and the
 * friend mark over numbered action rows (keys 1–9 work too). Without one: a filter field over the tab list; a click
 * on a nick opens its actions. A command row only opens the chat box with the command typed in.
 */
final class PlayerMenuScreen extends UiScreen {
    private static final String L = "layout.player_menu.";

    private final PlayerMenuModule module;
    private @Nullable String nick;
    private @Nullable UUID uuid;
    private final StringSetting query = new StringSetting("query", "", 16, false);
    private final TextField field = new TextField(query, () -> scroll = 0).placeholder(() -> Ui.tr("skirmish.player_menu.search"));
    private final Map<String, Row> pickRows = new HashMap<>();
    private List<Row> actions = List.of();
    private float scroll;
    private float maxScroll;

    PlayerMenuScreen(PlayerMenuModule module, @Nullable String nick, @Nullable UUID uuid) {
        super(Component.translatable("skirmish.module.player_menu.name"), null);
        this.module = module;
        select(nick, uuid);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        if (nick == null) {
            focus(field);
        }
    }

    private void select(@Nullable String nick, @Nullable UUID uuid) {
        this.nick = nick;
        this.uuid = uuid;
        this.actions = nick == null ? List.of() : buildActions(nick);
    }

    private List<Row> buildActions(String name) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(() -> Ui.tr("skirmish.player_menu.copy"), () -> Ui.tr("skirmish.player_menu.copy_hint"), this::copyNick));
        boolean commands = MenuCommands.validNick(name);
        if (commands && module.auction.get()) {
            rows.add(commandRow("skirmish.player_menu.auction", MenuCommands.Command.AUCTION, name));
        }
        if (commands && module.pay.get()) {
            rows.add(commandRow("skirmish.player_menu.pay", MenuCommands.Command.PAY, name));
        }
        if (commands && module.message.get()) {
            rows.add(commandRow("skirmish.player_menu.message", MenuCommands.Command.MESSAGE, name));
        }
        if (commands && module.clanInvite.get()) {
            rows.add(commandRow("skirmish.player_menu.clan_invite", MenuCommands.Command.CLAN_INVITE, name));
        }
        if (Friends.available() && MenuCommands.validNick(name)) {
            rows.add(new Row(() -> Ui.tr(Friends.isFriend(uuid, name) ? "skirmish.player_menu.friend_remove" : "skirmish.player_menu.friend_add"),
                    () -> Ui.tr("skirmish.player_menu.friend_hint"), () -> {
                boolean now = Friends.toggle(uuid, name);
                actionBar(Component.translatable(now ? "skirmish.player_menu.friend_added" : "skirmish.player_menu.friend_removed", name));
            }));
        }
        return rows;
    }

    private Row commandRow(String key, MenuCommands.Command command, String name) {
        String text = command.text(name);
        return new Row(() -> Ui.tr(key), text::strip, () -> openChat(text));
    }

    private void copyNick() {
        Minecraft mc = Minecraft.getInstance();
        if (nick != null) {
            mc.keyboardHandler.setClipboard(nick);
            module.log("copied nick %s", nick);
            mc.setScreen(null);
            actionBar(Component.translatable("skirmish.player_menu.copied", nick));
        }
    }

    /** Opens the chat box with {@code text} typed in; the user sends it (or not) with Enter. */
    private void openChat(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getChatStatus().isChatAllowed(mc.isLocalServer())) {
            mc.setScreen(null);
            actionBar(mc.getChatStatus().getMessage());
            return;
        }
        module.log("chat prefilled: %s", text);
        mc.setScreen(new ChatScreen(text, false));
    }

    private static void actionBar(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, true);
        }
    }

    private List<String> tabNames() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        List<String> names = new ArrayList<>();
        if (connection != null) {
            for (PlayerInfo info : connection.getListedOnlinePlayers()) {
                names.add(info.getProfile().name());
            }
        }
        String self = mc.player == null ? "" : mc.player.getGameProfile().name();
        return MenuCommands.filter(names, query.get(), self);
    }

    private @Nullable UUID uuidOf(String name) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(name);
        return info == null ? null : info.getProfile().id();
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        ui.rect(0, 0, ui.width(), ui.height(), 0, ui.color("backdrop"));
        float stroke = ui.num("stroke.width");
        float w = ui.num(L + "width");
        float padX = ui.num(L + "pad_x");
        float padY = ui.num(L + "pad_y");
        float rowH = ui.num(L + "row_height");
        float gap = ui.num(L + "row_gap");
        float cw = w - (stroke + padX) * 2;

        String currentNick = nick;
        List<String> sub = new ArrayList<>();
        List<String> names = currentNick == null ? tabNames() : List.of();
        float listH;
        if (currentNick == null) {
            listH = ui.theme().integer(L + "picker_rows") * (rowH + gap);
        } else {
            String dossier = uuid == null ? null : DossierModule.summary(uuid);
            if (dossier != null) {
                sub.add(dossier);
            }
            if (Friends.isFriend(uuid, currentNick)) {
                sub.add(Ui.tr("skirmish.player_menu.is_friend"));
            }
            listH = actions.size() * (rowH + gap);
        }
        float headerH = ui.lineHeight("pm_title") + sub.size() * ui.lineHeight("pm_sub");
        float fieldH = currentNick == null ? ui.num(L + "field_height") + ui.num(L + "header_gap") : 0;
        float h = stroke * 2 + padY * 2 + headerH + ui.num(L + "header_gap") + fieldH + listH + ui.lineHeight("pm_hint");
        float ox = (ui.width() - w) / 2f;
        float oy = (ui.height() - h) / 2f + Math.round((1f - appearProgress()) * ui.num("layout.appear_offset"));
        ui.box(ox, oy, w, h, ui.theme().radius("window"), ui.color("window"), ui.color("stroke_07"));

        float x = ox + stroke + padX;
        float y = oy + stroke + padY;
        String title = currentNick == null ? Ui.tr("skirmish.player_menu.pick_title") : currentNick;
        ui.text("pm_title", ui.ellipsize("pm_title", title, cw), x, y);
        y += ui.lineHeight("pm_title");
        for (String line : sub) {
            ui.text("pm_sub", ui.ellipsize("pm_sub", line, cw), x, y);
            y += ui.lineHeight("pm_sub");
        }
        y += ui.num(L + "header_gap");

        if (currentNick == null) {
            field.bounds(x, y, cw, ui.num(L + "field_height"));
            widget(ui, field, mx, my);
            y += ui.num(L + "field_height") + ui.num(L + "header_gap");
            float top = y;
            float bottom = y + listH;
            pushClip(ui, ox, top, ox + w, bottom);
            float ry = top - scroll;
            if (names.isEmpty()) {
                ui.text("pm_hint", Ui.tr("skirmish.player_menu.nobody"), x, ry + (rowH - ui.lineHeight("pm_hint")) / 2f);
            }
            for (String name : names) {
                Row row = pickRows.computeIfAbsent(name, n -> new Row(() -> n, () -> "", () -> select(n, uuidOf(n))));
                row.index = 0;
                row.bounds(x, ry, cw, rowH);
                widget(ui, row, mx, my);
                ry += rowH + gap;
            }
            popClip(ui);
            maxScroll = Math.max(0f, names.size() * (rowH + gap) - listH);
            scroll = Math.max(0f, Math.min(scroll, maxScroll));
            y = bottom;
        } else {
            for (int i = 0; i < actions.size(); i++) {
                Row row = actions.get(i);
                row.index = i + 1;
                row.bounds(x, y, cw, rowH);
                widget(ui, row, mx, my);
                y += rowH + gap;
            }
        }
        ui.text("pm_hint", ui.ellipsize("pm_hint", Ui.tr(currentNick == null ? "skirmish.player_menu.pick_hint" : "skirmish.player_menu.hint"), cw), x, y);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) scrollY * Theme.get().num("layout.menu.scroll_step")));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (nick != null) {
            int n = event.key() - GLFW.GLFW_KEY_1;
            if (n < 0 || n > 8) {
                n = event.key() - GLFW.GLFW_KEY_KP_1;
            }
            if (n >= 0 && n <= 8 && n < actions.size()) {
                actions.get(n).action.run();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /** One clickable line: optional number badge, a label and a muted hint on the right. */
    private static final class Row extends Widget {
        private final Supplier<String> label;
        private final Supplier<String> hint;
        final Runnable action;
        int index;

        Row(Supplier<String> label, Supplier<String> hint, Runnable action) {
            this.label = label;
            this.hint = hint;
            this.action = action;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            float t = hovered();
            ui.rect(x, y, w, h, ui.theme().radius("button_sm"), Anim.lerpColor(ui.color("fill_00"), ui.color("fill_06"), t));
            float tx = x + ui.num(L + "row_pad_x");
            if (index > 0) {
                float badge = ui.num(L + "badge");
                float by = y + (h - badge) / 2f;
                ui.rect(tx, by, badge, badge, ui.theme().radius("chip"), ui.color("fill_08"));
                String n = Integer.toString(index);
                ui.textCentered("pm_badge", n, tx + (badge - ui.textWidth("pm_badge", n)) / 2f, by, badge);
                tx += badge + ui.num(L + "badge_gap");
            }
            String h2 = hint.get();
            float hintW = h2.isEmpty() ? 0 : Math.min(ui.textWidth("pm_row_hint", h2), w * 0.5f);
            float labelW = x + w - ui.num(L + "row_pad_x") - hintW - ui.num(L + "badge_gap") - tx;
            ui.textCentered("pm_row", ui.ellipsize("pm_row", label.get(), labelW), tx, y, h,
                    Anim.lerpColor(ui.color("text_2"), ui.color("text"), t));
            if (!h2.isEmpty()) {
                String shown = ui.ellipsize("pm_row_hint", h2, hintW);
                ui.textCentered("pm_row_hint", shown, x + w - ui.num(L + "row_pad_x") - ui.textWidth("pm_row_hint", shown), y, h);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && enabled) {
                action.run();
                return true;
            }
            return false;
        }
    }
}
