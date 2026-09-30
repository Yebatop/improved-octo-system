package dev.skirmish.module.friends;

import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Read-only friend API for other modules. Every call answers false while the «Друзья» module is off or blocked by
 * Feature Control, so callers need no checks of their own. Client thread only.
 */
public final class Friends {
    private Friends() {
    }

    /** By UUID; a friend added by nick only is found through the tab list's nick for that UUID. */
    public static boolean isFriend(@Nullable UUID uuid) {
        FriendsModule module = FriendsModule.instance();
        return uuid != null && module != null && module.isEnabled() && module.isFriend(uuid, module.tabName(uuid));
    }

    /** By nick, ignoring case. */
    public static boolean isFriend(@Nullable String name) {
        FriendsModule module = FriendsModule.instance();
        return name != null && module != null && module.isEnabled() && module.isFriend(null, name);
    }

    /** UUID when both sides know it, else the nick. */
    public static boolean isFriend(@Nullable UUID uuid, @Nullable String name) {
        FriendsModule module = FriendsModule.instance();
        return (uuid != null || name != null) && module != null && module.isEnabled() && module.isFriend(uuid, name);
    }

    public static boolean isFriend(@Nullable Player player) {
        return player != null && isFriend(player.getUUID(), player.getGameProfile().name());
    }

    /** Whether the «Друзья» module is on (and not blocked), so the list can be edited. */
    public static boolean available() {
        FriendsModule module = FriendsModule.instance();
        return module != null && module.isEnabled();
    }

    /**
     * Adds the player, or removes them when already a friend (a user's click, e.g. in the player menu). Returns
     * whether they are a friend afterwards; nothing changes while the module is off.
     */
    public static boolean toggle(@Nullable UUID uuid, String name) {
        FriendsModule module = FriendsModule.instance();
        if (module == null || !module.isEnabled()) {
            return false;
        }
        FriendList current = module.list.get();
        if (current.contains(uuid, name)) {
            module.list.set(current.remove(uuid, name));
            module.log("removed " + name + " (player menu)");
            return false;
        }
        FriendList next = current.add(name, uuid);
        module.list.set(next);
        module.log("added " + name + " " + uuid + " (player menu)");
        return next.contains(uuid, name);
    }

    /** The saved list (empty while the module is off). */
    public static FriendList list() {
        FriendsModule module = FriendsModule.instance();
        return module == null || !module.isEnabled() ? FriendList.EMPTY : module.list.get();
    }
}
