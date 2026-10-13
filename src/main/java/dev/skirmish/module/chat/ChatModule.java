package dev.skirmish.module.chat;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.chat.mixin.ChatComponentAccessor;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * «Chat»: the chat in the mod's style. One rounded panel under the lines instead of a black strip per line; tabs
 * over the open chat (Все / Чат / Торговля / Ивенты / Система) with unread counts; a line repeated within two
 * minutes becomes one line with « ×N» at the newest place; lines naming you are marked with an accent bar; event
 * cards fold into one line; optional times. The server's text, colours and links stay as they are and clicks work
 * as in vanilla. Nothing is sent: only what is shown changes. Feature Control id {@code chat}.
 */
public final class ChatModule extends Module {
    public static final String ID = "chat";
    private static @Nullable ChatModule instance;

    final BoolSetting panel = add(new BoolSetting("panel", true));
    final NumberSetting opacity = add(new NumberSetting("opacity", 70, 0, 100, 5).unit("%"));
    final BoolSetting tabs = add(new BoolSetting("tabs", true));
    final BoolSetting stack = add(new BoolSetting("stack", true));
    final BoolSetting mentions = add(new BoolSetting("mentions", true));
    final BoolSetting cards = add(new BoolSetting("cards", true));
    final BoolSetting timestamps = add(new BoolSetting("timestamps", false));

    private ChatTabs.Tab tab = ChatTabs.Tab.ALL;
    private final Map<ChatTabs.Tab, Integer> unread = new EnumMap<>(ChatTabs.Tab.class);
    private final CardCompactor<Component> compactor = new CardCompactor<>(ChatModule::compose);
    private boolean adding;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    public ChatModule() {
        super(ID, true);
        opacity.under(panel).visibleWhen(panel::get);
    }

    @Override
    public Category category() {
        return Category.INTERFACE;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        instance = this;
    }

    @Override
    protected void onDisable() {
        selectTab(ChatTabs.Tab.ALL);
    }

    static @Nullable ChatModule active() {
        ChatModule m = instance;
        return m != null && m.isEnabled() ? m : null;
    }

    // ---- panel ----

    /** The chat drawing wrapped to draw one panel, or as it is when the panel is off. */
    public static ChatComponent.ChatGraphicsAccess wrap(ChatComponent.ChatGraphicsAccess access,
                                                        net.minecraft.client.gui.GuiGraphics graphics) {
        ChatModule m = active();
        return m != null && m.panel.get() ? new ChatPanelAccess(access, graphics, m.opacity.get().floatValue() / 100f) : access;
    }

    // ---- tabs ----

    ChatTabs.Tab tab() {
        return tab;
    }

    int unread(ChatTabs.Tab t) {
        return unread.getOrDefault(t, 0);
    }

    boolean tabsShown() {
        return tabs.get();
    }

    void selectTab(ChatTabs.Tab next) {
        unread.remove(next);
        if (next == tab) {
            return;
        }
        tab = next;
        Minecraft.getInstance().gui.getChat().rescaleChat();
    }

    /** Whether a message goes on the lines shown (the tab's filter; everything is kept either way). */
    public static boolean shows(GuiMessage message) {
        ChatModule m = active();
        if (m == null || !m.tabs.get() || m.tab == ChatTabs.Tab.ALL) {
            return true;
        }
        return ChatTabs.classify(message.content().getString()) == m.tab;
    }

    // ---- incoming lines ----

    /**
     * A message on its way into the chat: true when this took it (a card line held, or the line re-added changed:
     * stacked, marked, timed). Lines re-added here pass straight through.
     */
    public static boolean onAdd(ChatComponent chat, Component content, @Nullable MessageSignature signature, @Nullable GuiMessageTag tag) {
        ChatModule m = active();
        if (m == null || m.adding) {
            return false;
        }
        String text = content.getString();
        if (m.cards.get() && signature == null) {
            CardCompactor.Out<Component> out = m.compactor.offer(content, text, ticks());
            for (Component held : out.add()) {
                m.addDirect(chat, held, null, tag);
            }
            if (out.taken()) {
                return true;
            }
        }
        Component shown = content;
        GuiMessageTag shownTag = tag;
        if (m.mentions.get() && mentionsMe(text)) {
            shownTag = mentionTag();
        }
        if (m.stack.get() && signature == null && ChatStack.stackable(text)) {
            int n = removeEarlier(chat, text);
            if (n > 1) {
                shown = content.copy().append(Component.literal(" ×" + n).withStyle(ChatFormatting.GRAY));
            }
        }
        if (m.timestamps.get()) {
            shown = Component.literal(LocalTime.now().format(TIME) + " ").withStyle(ChatFormatting.DARK_GRAY).append(shown);
        }
        if (shown == content && shownTag == tag) {
            m.countUnread(text);
            return false;
        }
        m.addDirect(chat, shown, signature, shownTag);
        return true;
    }

    /** A card held too long goes out (called every chat tick). */
    public static void tick(ChatComponent chat) {
        ChatModule m = active();
        if (m != null) {
            for (Component held : m.compactor.flushIfStale(ticks())) {
                m.addDirect(chat, held, null, GuiMessageTag.system());
            }
        }
    }

    private void addDirect(ChatComponent chat, Component content, @Nullable MessageSignature signature, @Nullable GuiMessageTag tag) {
        adding = true;
        try {
            chat.addMessage(content, signature, tag);
        } finally {
            adding = false;
        }
        countUnread(content.getString());
    }

    private void countUnread(String text) {
        if (tab == ChatTabs.Tab.ALL || !tabs.get()) {
            return;
        }
        ChatTabs.Tab t = ChatTabs.classify(text);
        if (t != tab) {
            unread.merge(t, 1, Integer::sum);
        }
    }

    private static long ticks() {
        return Minecraft.getInstance().gui.getGuiTicks();
    }

    /** Takes an earlier copy of the line (within {@link ChatStack#LOOK_BACK} lines and two minutes) out; its count + 1. */
    private static int removeEarlier(ChatComponent chat, String text) {
        ChatComponentAccessor access = (ChatComponentAccessor) chat;
        List<GuiMessage> all = access.skirmish$allMessages();
        int now = Minecraft.getInstance().gui.getGuiTicks();
        String base = ChatStack.base(stripTime(text));
        for (int i = 0; i < Math.min(all.size(), ChatStack.LOOK_BACK); i++) {
            GuiMessage g = all.get(i);
            if (now - g.addedTime() > ChatStack.MAX_AGE_TICKS) {
                break;
            }
            String earlier = stripTime(g.content().getString());
            if (g.signature() == null && ChatStack.base(earlier).equals(base)) {
                all.remove(i);
                access.skirmish$refreshTrimmedMessages();
                return ChatStack.count(earlier) + 1;
            }
        }
        return 1;
    }

    private static final Pattern TIME_PREFIX = Pattern.compile("^\\d\\d:\\d\\d ");

    private static String stripTime(String text) {
        return TIME_PREFIX.matcher(text).replaceFirst("");
    }

    private static boolean mentionsMe(String text) {
        Minecraft mc = Minecraft.getInstance();
        String name = mc.player != null ? mc.player.getGameProfile().name() : mc.getUser().getName();
        String body = ChatTabs.body(text);
        if (name == null || name.length() < 3 || body == null) {
            return false;
        }
        return Pattern.compile("(?<![\\p{L}\\d_])" + Pattern.quote(name) + "(?![\\p{L}\\d_])", Pattern.CASE_INSENSITIVE)
                .matcher(body).find();
    }

    private static GuiMessageTag mentionTag() {
        return new GuiMessageTag(Theme.get().color("accent") & 0xFFFFFF, null,
                Component.translatable("skirmish.chat.mention"), "Mention");
    }

    // ---- event cards ----

    /** «⚡ Опытный Тыпо · Эпическая · 109 69 356 [+метка]»: the card's lines without their bars and labels. */
    private static Component compose(CardCompactor.Card<Component> card) {
        MutableComponent out = Component.literal("⚡ ").withStyle(ChatFormatting.GOLD);
        out.append(dropUntil(card.title(), "▍"));
        for (String value : card.fieldTexts()) {
            out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            out.append(Component.literal(value).withStyle(ChatFormatting.YELLOW));
        }
        if (card.coords() != null) {
            out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            out.append(dropUntil(card.coords(), ":"));
        }
        return out;
    }

    /** The line with everything up to and including the first {@code mark} (and the spaces after it) left out. */
    static Component dropUntil(Component line, String mark) {
        String text = line.getString();
        int at = text.indexOf(mark);
        if (at < 0) {
            return line;
        }
        int skip = at + mark.length();
        while (skip < text.length() && Character.isWhitespace(text.charAt(skip))) {
            skip++;
        }
        MutableComponent out = Component.empty();
        int[] seen = {0};
        int from = skip;
        line.visit((Style style, String part) -> {
            int start = seen[0];
            int end = start + part.length();
            seen[0] = end;
            if (end > from) {
                out.append(Component.literal(part.substring(Math.max(0, from - start))).withStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }
}
