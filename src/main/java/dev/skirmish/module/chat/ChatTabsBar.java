package dev.skirmish.module.chat;

import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * The tab pills over the open chat, right above its top line (where vanilla's lines would reach at full height):
 * the open tab in the accent colour, unread counts on the others. Clicks on them switch the tab; the rest of the
 * chat screen works as usual.
 */
public final class ChatTabsBar {
    private static final String L = "layout.chat.";
    /** Pills as last drawn: x0, y0, x1, y1 (design px) and the tab's ordinal. */
    private static final List<float[]> HITS = new ArrayList<>();

    private ChatTabsBar() {
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        HITS.clear();
        ChatModule m = ChatModule.active();
        Minecraft mc = Minecraft.getInstance();
        if (m == null || !m.tabsShown() || mc.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) {
            return;
        }
        Ui ui = Ui.begin(graphics);
        try {
            float s = Ui.designScale();
            float mx = mouseX / s;
            float my = mouseY / s;
            // In the free strip between the chat's last line (40 px above the bottom) and the input box (14 px).
            float strip = (INPUT_TOP + 3f) / s;
            float th = Math.min(ui.num(L + "tab_height"), (CHAT_BOTTOM - INPUT_TOP - 4f) / s);
            float x = ui.num(L + "tab_margin") / 2f;
            float y = ui.height() - strip - th;
            float padX = ui.num(L + "tab_pad_x");
            for (ChatTabs.Tab tab : ChatTabs.Tab.values()) {
                boolean on = tab == m.tab();
                String style = on ? "chat_tab_on" : "chat_tab";
                String label = Ui.tr("skirmish.chat.tab." + tab.key());
                int unread = on ? 0 : m.unread(tab);
                String badge = unread <= 0 ? null : unread > 99 ? "99+" : Integer.toString(unread);
                float badgeW = badge == null ? 0f : ui.textWidth("chat_badge", badge) + ui.num(L + "badge_pad_x") * 2;
                float w = ui.textWidth(style, label) + padX * 2 + (badge == null ? 0f : badgeW + ui.num(L + "badge_gap"));
                boolean hover = mx >= x && mx < x + w && my >= y && my < y + th;
                int fill = on ? ui.color("accent") : ui.color(hover ? "chat_tab_hover" : "chat_tab");
                ui.box(x, y, w, th, th / 2f, fill, on ? fill : ui.color("chat_stroke"));
                float tx = ui.textCentered(style, label, x + padX, y, th, on ? ui.color("text") : ui.color(hover ? "text" : "text_2"));
                if (badge != null) {
                    float bh = ui.lineHeight("chat_badge") + 2;
                    float bx = tx + ui.num(L + "badge_gap");
                    ui.rect(bx, y + (th - bh) / 2f, badgeW, bh, bh / 2f, ui.color("accent"));
                    ui.textCentered("chat_badge", badge, bx + ui.num(L + "badge_pad_x"), y + (th - bh) / 2f, bh);
                }
                HITS.add(new float[]{x, y, x + w, y + th, tab.ordinal()});
                x += w + ui.num(L + "tab_gap");
            }
        } finally {
            ui.end();
        }
    }

    /** A left click at (x, y) GUI px on a pill: switches to that tab. */
    public static boolean click(double x, double y, int button) {
        ChatModule m = ChatModule.active();
        if (m == null || button != 0) {
            return false;
        }
        double dx = Ui.toDesign(x);
        double dy = Ui.toDesign(y);
        for (float[] hit : HITS) {
            if (dx >= hit[0] && dx < hit[2] && dy >= hit[1] && dy < hit[3]) {
                m.selectTab(ChatTabs.Tab.values()[(int) hit[4]]);
                return true;
            }
        }
        return false;
    }

    /** Vanilla's layout, GUI px from the bottom: the chat's last line ends 40 up, the input box starts 14 up. */
    private static final float CHAT_BOTTOM = 40f;
    private static final float INPUT_TOP = 14f;
}
