package dev.skirmish.module.friends;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.ActionSetting;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * «Друзья»: a friend list kept in config.json, edited with {@code /skirmish friend add|remove|list <nick>} (a client
 * command, never sent to the server) or the «добавить/убрать друга» key on the player under the crosshair. Friends'
 * nametags can be colored; other modules ask {@link Friends}. Nothing is automated: the list only changes on the
 * user's own command or key press.
 */
public final class FriendsModule extends Module {
    public static final String ID = "friends";
    /** How far the key looks for a player (vanilla draws nametags up to 64 blocks). */
    static final double PICK_RANGE = 64;
    private static final int CLANSHARE_REFRESH_TICKS = 40;
    private static final String CLANSHARE_SOURCE = "clanshare:";
    private static @Nullable FriendsModule instance;

    public enum FriendColor {
        GREEN, AQUA, BLUE, VIOLET, PINK, GOLD;

        /** Theme color token. */
        String token() {
            return "friend_" + name().toLowerCase(Locale.ROOT);
        }
    }

    final FriendListSetting list = add(new FriendListSetting("list"));
    final BoolSetting colorNametags = add(new BoolSetting("color_nametags", true));
    final EnumSetting<FriendColor> color = (EnumSetting<FriendColor>) add(new EnumSetting<>("color", FriendColor.GREEN))
            .under(colorNametags).visibleWhen(colorNametags::get);
    final BoolSetting marker = (BoolSetting) add(new BoolSetting("marker", false))
            .under(colorNametags).visibleWhen(colorNametags::get);
    final BoolSetting clanShare = add(new BoolSetting("clanshare", false));
    final ActionSetting importClanShare = (ActionSetting) add(new ActionSetting("import_clanshare", this::importClanShare))
            .under(clanShare);
    final KeySetting key = add(new KeySetting("key", "key.skirmish.friends.toggle"));
    final ActionSetting showList = add(new ActionSetting("show_list", this::printList));

    private Set<String> clanShareNicks = Set.of();
    private int refreshTicks;

    public FriendsModule() {
        super(ID, true);
        instance = this;
    }

    static @Nullable FriendsModule instance() {
        return instance;
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
        // Polled while the module is off too, so presses made while it was off do not fire later.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (SkirmishKeys.FRIEND_TOGGLE.consumeClick()) {
                if (isEnabled() && mc.player != null && mc.screen == null) {
                    toggleUnderCrosshair(mc);
                }
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommand(dispatcher));
    }

    @Override
    public void tick() {
        if (!clanShare.get()) {
            clanShareNicks = Set.of();
            return;
        }
        if (--refreshTicks <= 0) {
            refreshTicks = CLANSHARE_REFRESH_TICKS;
            clanShareNicks = readClanShareNicks();
        }
    }

    @Override
    protected void onDisable() {
        clanShareNicks = Set.of();
        refreshTicks = 0;
    }

    // ---- queries (Friends API and the nametag hook) ----

    boolean isFriend(@Nullable UUID uuid, @Nullable String name) {
        if (list.get().contains(uuid, name)) {
            return true;
        }
        return name != null && clanShare.get() && clanShareNicks.contains(name.toLowerCase(Locale.ROOT));
    }

    @Nullable String tabName(UUID uuid) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(uuid);
        return info == null ? null : info.getProfile().name();
    }

    /** RGB to color a friend's nametag with, or -1 to leave the nametag as the server sent it. */
    public static int nametagColor(Player player) {
        FriendsModule module = instance;
        if (module == null || !module.isEnabled() || !module.colorNametags.get()
                || !module.isFriend(player.getUUID(), player.getGameProfile().name())) {
            return -1;
        }
        return Theme.get().color(module.color.get().token()) & 0xFFFFFF;
    }

    /** Whether friends' nametags also get a small marker in front of the nick. */
    public static boolean nametagMarker() {
        FriendsModule module = instance;
        return module != null && module.marker.get();
    }

    // ---- editing ----

    private void toggleUnderCrosshair(Minecraft mc) {
        Player target = PlayerPick.find(mc, PICK_RANGE);
        if (target == null) {
            actionBar(mc, Component.translatable("skirmish.friends.no_target"));
            return;
        }
        GameProfile profile = target.getGameProfile();
        FriendList current = list.get();
        if (current.contains(profile.id(), profile.name())) {
            list.set(current.remove(profile.id(), profile.name()));
            log("removed " + profile.name() + " (key)");
            actionBar(mc, Component.translatable("skirmish.friends.removed", profile.name()));
        } else {
            list.set(current.add(profile.name(), profile.id()));
            log("added " + profile.name() + " " + profile.id() + " (key)");
            actionBar(mc, Component.translatable("skirmish.friends.added", profile.name()));
        }
    }

    /** Adds a nick; the UUID comes from the tab list when the player is online. Returns the message to show. */
    Component addByName(String name) {
        if (!FriendList.isValidName(name)) {
            return Component.translatable("skirmish.friends.invalid_name", name).withStyle(ChatFormatting.RED);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getGameProfile().name().equalsIgnoreCase(name)) {
            return Component.translatable("skirmish.friends.self").withStyle(ChatFormatting.RED);
        }
        UUID uuid = null;
        String exact = name;
        ClientPacketListener connection = mc.getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfoIgnoreCase(name);
        if (info != null) {
            uuid = info.getProfile().id();
            exact = info.getProfile().name();
        }
        FriendList current = list.get();
        FriendList next = current.add(exact, uuid);
        if (current.find(uuid, exact) != null) {
            // Already a friend; the entry may still learn its UUID or new nick.
            list.set(next);
            return Component.translatable("skirmish.friends.already", exact);
        }
        if (next == current) {
            return Component.translatable("skirmish.friends.full", FriendList.MAX_SIZE).withStyle(ChatFormatting.RED);
        }
        list.set(next);
        log("added " + exact + (uuid == null ? " (offline, nick only)" : " " + uuid) + " (command)");
        return Component.translatable("skirmish.friends.added", exact);
    }

    Component removeByName(String nameOrUuid) {
        FriendList current = list.get();
        FriendList next = current.remove(nameOrUuid);
        if (next == current) {
            return Component.translatable("skirmish.friends.not_found", nameOrUuid).withStyle(ChatFormatting.RED);
        }
        list.set(next);
        log("removed " + nameOrUuid + " (command)");
        return Component.translatable("skirmish.friends.removed", nameOrUuid);
    }

    /** Header plus one line per friend: online dot, nick, a click-to-fill «убрать» link. */
    List<Component> listLines() {
        List<Component> lines = new ArrayList<>();
        FriendList current = list.get();
        if (current.isEmpty()) {
            lines.add(Component.translatable("skirmish.friends.empty"));
            return lines;
        }
        lines.add(Component.translatable("skirmish.friends.list", current.size()));
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        int online = Theme.get().color("good") & 0xFFFFFF;
        int offline = Theme.get().color("text_3") & 0xFFFFFF;
        int nameColor = Theme.get().color(color.get().token()) & 0xFFFFFF;
        for (String name : current.names()) {
            boolean here = connection != null && connection.getPlayerInfoIgnoreCase(name) != null;
            MutableComponent line = Component.literal(" ● ").withStyle(Style.EMPTY.withColor(here ? online : offline));
            line.append(Component.literal(name).withStyle(Style.EMPTY.withColor(nameColor)));
            line.append(Component.literal("  "));
            line.append(Component.translatable("skirmish.friends.remove_link").withStyle(Style.EMPTY.withColor(offline)
                    .withClickEvent(new ClickEvent.SuggestCommand("/skirmish friend remove " + name))
                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("skirmish.friends.remove_hover", name)))));
            lines.add(line);
        }
        return lines;
    }

    private void printList() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        for (Component line : listLines()) {
            mc.gui.getChat().addMessage(line);
        }
    }

    /** Copies the nicks of ClanShare waypoints on this server into the list (menu button, once per press). */
    private void importClanShare() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        FriendList current = list.get();
        FriendList next = current;
        for (String nick : readClanShareNicksExact()) {
            if (!nick.equalsIgnoreCase(mc.player.getGameProfile().name()) && !next.containsName(nick)) {
                next = next.add(nick, null);
            }
        }
        int added = next.size() - current.size();
        if (added > 0) {
            list.set(next);
        }
        log("imported " + added + " ClanShare senders");
        mc.gui.getChat().addMessage(added > 0 ? Component.translatable("skirmish.friends.imported", added)
                : Component.translatable("skirmish.friends.import_none"));
    }

    private static Set<String> readClanShareNicks() {
        Set<String> out = new HashSet<>();
        for (String nick : readClanShareNicksExact()) {
            out.add(nick.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(out);
    }

    /** Nicks of players whose ClanShare labels became waypoints on this server (source {@code clanshare:Nick}). */
    private static List<String> readClanShareNicksExact() {
        List<String> out = new ArrayList<>();
        try {
            for (Waypoint waypoint : WaypointManager.get().currentServer()) {
                String source = waypoint.source();
                if (source.startsWith(CLANSHARE_SOURCE)) {
                    String nick = source.substring(CLANSHARE_SOURCE.length());
                    if (FriendList.isValidName(nick) && out.stream().noneMatch(nick::equalsIgnoreCase)) {
                        out.add(nick);
                    }
                }
            }
        } catch (IllegalStateException e) {
            // Waypoints not installed yet.
        }
        return out;
    }

    private static void actionBar(Minecraft mc, Component message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(message, true);
        }
    }

    // ---- command ----

    private void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        SuggestionProvider<FabricClientCommandSource> online = (ctx, builder) -> {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            List<String> names = new ArrayList<>();
            if (connection != null) {
                for (PlayerInfo info : connection.getOnlinePlayers()) {
                    String name = info.getProfile().name();
                    if (FriendList.isValidName(name) && !list.get().containsName(name)) {
                        names.add(name);
                    }
                }
            }
            return SharedSuggestionProvider.suggest(names, builder);
        };
        SuggestionProvider<FabricClientCommandSource> friends = (ctx, builder) -> SharedSuggestionProvider.suggest(list.get().names(), builder);
        // Merged into the core "/skirmish" node by Brigadier.
        dispatcher.register(literal("skirmish").then(literal("friend")
                .executes(this::runList)
                .then(literal("list").executes(this::runList))
                .then(literal("add").then(argument("nick", StringArgumentType.word()).suggests(online)
                        .executes(ctx -> run(ctx, () -> addByName(StringArgumentType.getString(ctx, "nick"))))))
                .then(literal("remove").then(argument("nick", StringArgumentType.word()).suggests(friends)
                        .executes(ctx -> run(ctx, () -> removeByName(StringArgumentType.getString(ctx, "nick"))))))));
    }

    private boolean blocked(CommandContext<FabricClientCommandSource> ctx) {
        if (isBlocked()) {
            ctx.getSource().sendError(Component.translatable("skirmish.friends.blocked"));
            return true;
        }
        return false;
    }

    private int run(CommandContext<FabricClientCommandSource> ctx, java.util.function.Supplier<Component> action) {
        if (blocked(ctx)) {
            return 0;
        }
        ctx.getSource().sendFeedback(action.get());
        if (!isSwitchedOn()) {
            ctx.getSource().sendFeedback(Component.translatable("skirmish.friends.module_off").withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private int runList(CommandContext<FabricClientCommandSource> ctx) {
        if (blocked(ctx)) {
            return 0;
        }
        for (Component line : listLines()) {
            ctx.getSource().sendFeedback(line);
        }
        return list.get().size();
    }
}
