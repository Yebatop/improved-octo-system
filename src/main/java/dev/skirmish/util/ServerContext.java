package dev.skirmish.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

import java.util.Locale;

/** Identifies "where we are": the server address (or singleplayer world) and the dimension id. */
public final class ServerContext {
    public static final String UNKNOWN = "unknown";

    private ServerContext() {
    }

    /** Stable key of the current server, e.g. {@code lite.holyworld.ru} or {@code singleplayer:New World}. */
    public static String serverKey() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer local = mc.getSingleplayerServer();
        if (local != null) {
            return "singleplayer:" + local.getWorldData().getLevelName();
        }
        ServerData data = mc.getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isBlank()) {
            return normalizeAddress(data.ip);
        }
        return UNKNOWN;
    }

    /** Human readable server name for cards and messages. */
    public static String serverDisplayName() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer local = mc.getSingleplayerServer();
        if (local != null) {
            return local.getWorldData().getLevelName();
        }
        ServerData data = mc.getCurrentServer();
        return data != null && data.ip != null ? data.ip : UNKNOWN;
    }

    /** Lower-cased host with the default port removed: {@code Lite.HolyWorld.ru:25565 -> lite.holyworld.ru}. */
    public static String normalizeAddress(String address) {
        String result = address.trim().toLowerCase(Locale.ROOT);
        if (result.endsWith(":25565")) {
            result = result.substring(0, result.length() - ":25565".length());
        }
        if (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /** Dimension id of the client level, e.g. {@code minecraft:overworld}. */
    public static String dimension() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return UNKNOWN;
        }
        return mc.level.dimension().identifier().toString();
    }
}
