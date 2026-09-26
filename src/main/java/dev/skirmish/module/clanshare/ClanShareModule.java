package dev.skirmish.module.clanshare;

import dev.skirmish.module.Category;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.Module;
import dev.skirmish.setting.ActionSetting;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.setting.StringSetting;
import dev.skirmish.util.ServerContext;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.jspecify.annotations.Nullable;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * ClanShare: {@code .share [name]} (or the share key) sends the player's position as one encrypted chat line;
 * clients with the same password show it as a clickable label that creates a waypoint. The typed {@code .share}
 * never reaches the server; the encrypted line is the only packet this module causes.
 */
public final class ClanShareModule extends Module {
    public static final String ID = "clanshare";

    private static final GuiMessageTag TAG = new GuiMessageTag(0x55FF55, null, Component.translatable("skirmish.clanshare.tag"), "ClanShare");
    private static final long PENDING_TIMEOUT_MS = 10_000;

    private final ClanKey clanKey = new ClanKey(this);
    private final SecureRandom random = new SecureRandom();

    final StringSetting password = add(new StringSetting("password", "", 64, true));
    final StringSetting chatPrefix = add(new StringSetting("chat_prefix", "", ShareCodec.MAX_PREFIX_LENGTH, false));
    final NumberSetting cooldown = add(new NumberSetting("cooldown", 5, 0, 60, 1).unit(" s"));
    final NumberSetting maxAge = add(new NumberSetting("max_age", 30, 0, 240, 5).unit(" min"));
    final BoolSetting selectOnAdd = add(new BoolSetting("select_on_add", true));
    final StringSetting blockedFragments = add(new StringSetting("blocked_fragments", "", 128, false));
    final ActionSetting showFingerprint = add(new ActionSetting("fingerprint", this::printFingerprint));

    private long lastShareMs;
    private @Nullable SharePayload pendingPayload;
    private long pendingSinceMs;
    private @Nullable String outgoingLine;
    private boolean sendingOwnLine;

    @Override
    public Category category() {
        return Category.WORLD;
    }

    public ClanShareModule() {
        super(ID, true);
        password.onChange(clanKey::setPassword);
    }

    @Override
    public void onInitialize() {
        ClientSendMessageEvents.ALLOW_CHAT.register(this::allowSend);
        ClientReceiveMessageEvents.MODIFY_GAME.register(this::modifyGame);
        ClientReceiveMessageEvents.ALLOW_CHAT.register(this::allowChat);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommand(dispatcher));
    }

    @Override
    protected void onDisable() {
        pendingPayload = null;
        outgoingLine = null;
    }

    @Override
    public void tick() {
        while (SkirmishKeys.CLANSHARE_SHARE.consumeClick()) {
            log("share key pressed");
            requestShare("");
        }
        SharePayload pending = pendingPayload;
        if (pending != null) {
            SecretKey key = clanKey.key();
            if (key != null) {
                pendingPayload = null;
                encodeAndQueue(pending, key);
            } else if (clanKey.state() != ClanKey.State.DERIVING || System.currentTimeMillis() - pendingSinceMs > PENDING_TIMEOUT_MS) {
                pendingPayload = null;
                log("queued share dropped, key state " + clanKey.state());
                showLocal(ShareText.error(Component.translatable("skirmish.clanshare.key_failed")));
            }
        }
        String line = outgoingLine;
        if (line != null) {
            outgoingLine = null;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                log("share not sent, left the world before the next tick");
                return;
            }
            sendingOwnLine = true;
            try {
                player.connection.sendChat(line);
            } finally {
                sendingOwnLine = false;
            }
            lastShareMs = System.currentTimeMillis();
        }
    }

    private boolean allowSend(String message) {
        if (sendingOwnLine) {
            return true;
        }
        String name = ShareCodec.parseShareCommand(message);
        if (name == null) {
            return true;
        }
        if (!isEnabled()) {
            log("intercepted '" + ShareCodec.SHARE_COMMAND + "' while the module is disabled, nothing sent");
            showLocal(ShareText.error(Component.translatable("skirmish.clanshare.disabled")));
            return false;
        }
        log("intercepted '" + ShareCodec.SHARE_COMMAND + "' from chat, not sent to the server (name " + name.length() + " chars)");
        requestShare(name);
        return false;
    }

    private void requestShare(String name) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            log("share refused: not in a world");
            return;
        }
        long now = System.currentTimeMillis();
        long waitMs = lastShareMs + (long) (cooldown.get() * 1000) - now;
        if (waitMs > 0 || outgoingLine != null || pendingPayload != null) {
            long seconds = Math.max(1, (waitMs + 999) / 1000);
            log("share refused: cooldown, " + waitMs + " ms left");
            showLocal(ShareText.error(Component.translatable("skirmish.clanshare.cooldown", seconds)));
            return;
        }
        String dimension = ServerContext.dimension();
        if (!SharePayload.isValidDimension(dimension)) {
            log("share refused: dimension id '" + dimension + "' cannot be encoded");
            showLocal(ShareText.error(Component.translatable("skirmish.clanshare.bad_dimension", dimension)));
            return;
        }
        GameProfile profile = player.getGameProfile();
        SharePayload payload = new SharePayload(now / 1000, player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                dimension, profile.name(), name);
        if (!payload.name().equals(name.strip())) {
            log("name cleaned/shortened from " + name.length() + " to " + payload.name().length() + " chars");
        }
        switch (clanKey.state()) {
            case NO_PASSWORD -> {
                log("share refused: no clan password set");
                showLocal(ShareText.error(Component.translatable("skirmish.clanshare.no_password")));
            }
            case FAILED -> {
                log("share refused: key derivation failed earlier");
                showLocal(ShareText.error(Component.translatable("skirmish.clanshare.key_failed")));
            }
            case DERIVING -> {
                log("key not ready yet, share queued until it is");
                pendingPayload = payload;
                pendingSinceMs = now;
                showLocal(ShareText.info(Component.translatable("skirmish.clanshare.key_pending")));
            }
            case READY -> {
                SecretKey key = clanKey.key();
                if (key != null) {
                    encodeAndQueue(payload, key);
                }
            }
        }
    }

    private void encodeAndQueue(SharePayload payload, SecretKey key) {
        List<String> blocked = ShareCodec.parseBlocked(blockedFragments.get());
        ShareCodec.Encoded encoded;
        try {
            encoded = ShareCodec.encode(payload, key, chatPrefix.get(), blocked, random);
        } catch (RuntimeException e) {
            error("share encoding failed", e);
            showLocal(ShareText.error(Component.translatable("skirmish.clanshare.encode_failed")));
            return;
        }
        SharePayload sent = encoded.sent();
        log("sending share: " + encoded.line().length() + "/" + ShareCodec.MAX_CHAT_LENGTH + " chars (prefix "
                + (encoded.line().length() - encoded.token().length()) + " + token " + encoded.token().length() + "), sealed "
                + encoded.sealedBytes() + " bytes, attempts " + encoded.attempts() + ", " + sent.coordsText() + " " + sent.dimension()
                + ", nick '" + sent.nick() + "', name '" + sent.name() + "'"
                + (sent.name().length() < payload.name().length() ? " (cut from " + payload.name().length() + " chars to fit)" : ""));
        outgoingLine = encoded.line();
        showLocal(ShareText.info(Component.translatable("skirmish.clanshare.sent", sent.coordsText())));
    }

    private Component modifyGame(Component message, boolean overlay) {
        if (!isEnabled() || overlay) {
            return message;
        }
        Component replaced = decode(message, "system");
        return replaced == null ? message : replaced;
    }

    private boolean allowChat(Component message, @Nullable PlayerChatMessage signed, @Nullable GameProfile sender,
                              ChatType.Bound params, Instant receptionTimestamp) {
        if (!isEnabled()) {
            return true;
        }
        Component replaced = decode(message, "chat from " + (sender == null ? "?" : sender.name()));
        if (replaced == null) {
            return true;
        }
        Minecraft.getInstance().gui.getChat().addMessage(replaced, signed == null ? null : signed.signature(), TAG);
        return false;
    }

    /** The line with the first valid token replaced by a label, or null to leave it untouched. */
    private @Nullable Component decode(Component message, String origin) {
        ShareText.Flat flat = new ShareText.Flat(message);
        String plain = flat.plain();
        List<ShareCodec.Token> tokens = ShareCodec.find(plain);
        if (tokens.isEmpty()) {
            if (isDebug() && ShareCodec.longestAlphabetRun(plain) >= ShareCodec.SUSPICIOUS_RUN) {
                log("ignored " + origin + ": long base30-like run without the '" + ShareCodec.MARKER + "' marker (marker stripped by the server?)");
            }
            return null;
        }
        SecretKey key = clanKey.key();
        if (key == null) {
            log("ignored " + origin + ": " + tokens.size() + " marker(s) found but no key (" + clanKey.state() + ")");
            return null;
        }
        for (ShareCodec.Token token : tokens) {
            ShareCodec.Opened opened = ShareCodec.open(token, key);
            if (opened instanceof ShareCodec.Opened.Rejected rejected) {
                log("dropped token at " + token.start() + " in " + origin + ": " + rejected.failure().description + " (" + rejected.detail() + ")");
                continue;
            }
            SharePayload payload = ((ShareCodec.Opened.Ok) opened).payload();
            long nowSeconds = System.currentTimeMillis() / 1000;
            long age = nowSeconds - payload.time();
            long maxAgeSeconds = Math.round(maxAge.get() * 60);
            if (!ShareCodec.isFresh(payload, nowSeconds, maxAgeSeconds)) {
                log("dropped share from '" + payload.nick() + "' in " + origin + ": stale, sent " + age + " s ago (limit " + maxAgeSeconds + " s; replay or clock skew)");
                continue;
            }
            log("decoded share in " + origin + ": '" + payload.nick() + "' -> '" + payload.name() + "' " + payload.coordsText() + " "
                    + payload.dimension() + ", age " + age + " s, " + token.detail());
            return flat.replace(token.start(), token.end(), ShareText.label(payload, ServerContext.dimension(), age));
        }
        return null;
    }

    private void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // Every input under this root must parse: an unparsable one would be forwarded to the server by Fabric.
        dispatcher.register(literal(ShareText.COMMAND)
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(ShareText.info(Component.translatable("skirmish.clanshare.command_usage")));
                    return 0;
                })
                .then(argument("args", StringArgumentType.greedyString()).executes(this::runAdd)));
    }

    private int runAdd(CommandContext<FabricClientCommandSource> ctx) {
        String args = StringArgumentType.getString(ctx, "args");
        SharePayload payload = SharePayload.fromCommandArgs(args);
        if (payload == null) {
            log("click command rejected: '" + args + "'");
            ctx.getSource().sendFeedback(ShareText.error(Component.translatable("skirmish.clanshare.command_usage")));
            return 0;
        }
        String source = "clanshare:" + payload.nick();
        String name = payload.name().isEmpty() ? payload.nick() : payload.name();
        WaypointManager manager = WaypointManager.get();
        Waypoint waypoint = existing(manager, payload, source);
        boolean created = waypoint == null;
        if (waypoint == null) {
            waypoint = manager.add(name, payload.x() + 0.5, payload.y(), payload.z() + 0.5, payload.dimension(), source);
        }
        if (selectOnAdd.get()) {
            manager.select(waypoint.id());
        }
        log((created ? "waypoint created" : "waypoint already existed") + " from label: '" + waypoint.name() + "' " + waypoint.coordsText()
                + " " + waypoint.dimension() + " (" + source + "), arrow " + (selectOnAdd.get() ? "selected" : "unchanged"));
        ctx.getSource().sendFeedback(ShareText.info(Component.translatable(created ? "skirmish.clanshare.waypoint_added" : "skirmish.clanshare.waypoint_exists",
                waypoint.name(), waypoint.coordsText())));
        return 1;
    }

    private static @Nullable Waypoint existing(WaypointManager manager, SharePayload payload, String source) {
        for (Waypoint waypoint : manager.currentServer()) {
            if (waypoint.source().equals(source) && waypoint.dimension().equals(payload.dimension())
                    && waypoint.coordsText().equals(payload.coordsText())) {
                return waypoint;
            }
        }
        return null;
    }

    private void printFingerprint() {
        String fingerprint = clanKey.fingerprint();
        Component text = switch (clanKey.state()) {
            case READY -> ShareText.info(Component.translatable("skirmish.clanshare.fingerprint", fingerprint == null ? "?" : fingerprint));
            case DERIVING -> ShareText.info(Component.translatable("skirmish.clanshare.key_pending"));
            case NO_PASSWORD -> ShareText.error(Component.translatable("skirmish.clanshare.no_password"));
            case FAILED -> ShareText.error(Component.translatable("skirmish.clanshare.key_failed"));
        };
        showLocal(text);
    }

    private static void showLocal(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.gui.getChat().addMessage(message);
        }
    }
}
