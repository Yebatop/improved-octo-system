package dev.skirmish.holyworld;

import dev.skirmish.util.ServerContext;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * Whether the client is connected to the HolyWorld network ({@code mc.holyworld.ru}, {@code mc.holyworld.me} and
 * their subdomains). HolyWorld-only features (Feature Control, api.holyworld.me, safe mode, server-specific
 * parsers) check this. {@code -Dskirmish.holyworld=true} treats any server as HolyWorld, for testing.
 */
public final class HolyWorld {
    private static final List<String> DOMAINS = List.of("holyworld.ru", "holyworld.me", "holyworld.io");

    private HolyWorld() {
    }

    public static boolean isConnected() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.getSingleplayerServer() != null) {
            return false;
        }
        return Boolean.getBoolean("skirmish.holyworld") || isHolyWorldAddress(ServerContext.serverKey());
    }

    /** {@code mc.holyworld.ru}, {@code HolyWorld.me:25565}, {@code lite.holyworld.ru.} → true. */
    public static boolean isHolyWorldAddress(String address) {
        String host = address.toLowerCase(Locale.ROOT).trim();
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            host = host.substring(0, colon);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        for (String domain : DOMAINS) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }
}
