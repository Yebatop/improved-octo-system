package dev.skirmish.module.analytics.feed;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Entries of the kill feed, newest first. A death reported twice (entity event and the kill attribution) merges
 * into one row. {@code W} is the weapon (an ItemStack in game; anything in tests). Pure Java, unit tested.
 */
public final class KillFeed<W> {
    /** Two reports of the same death (or totem) within this window are one row. */
    static final long MERGE_MS = 3_000;
    static final int KEPT = 32;

    public enum Kind {
        KILL, TOTEM
    }

    public static final class Entry<W> {
        private final Kind kind;
        private final UUID victimUuid;
        private final String victim;
        private final boolean victimMe;
        private final long timeMs;
        private @Nullable String killer;
        private boolean killerMe;
        private @Nullable W weapon;

        Entry(Kind kind, UUID victimUuid, String victim, boolean victimMe, @Nullable String killer, boolean killerMe,
              @Nullable W weapon, long timeMs) {
            this.kind = kind;
            this.victimUuid = victimUuid;
            this.victim = victim;
            this.victimMe = victimMe;
            this.killer = killer;
            this.killerMe = killerMe;
            this.weapon = weapon;
            this.timeMs = timeMs;
        }

        public Kind kind() {
            return kind;
        }

        public UUID victimUuid() {
            return victimUuid;
        }

        public String victim() {
            return victim;
        }

        public boolean victimMe() {
            return victimMe;
        }

        /** Killer name, null when unknown ("?"). */
        public @Nullable String killer() {
            return killer;
        }

        public boolean killerMe() {
            return killerMe;
        }

        public @Nullable W weapon() {
            return weapon;
        }

        public long timeMs() {
            return timeMs;
        }
    }

    private final LinkedList<Entry<W>> entries = new LinkedList<>();

    /** A death (or totem pop); merges with the same victim's row of the same kind within {@link #MERGE_MS}. */
    public Entry<W> add(Kind kind, UUID victimUuid, String victim, boolean victimMe, @Nullable String killer, boolean killerMe,
                        @Nullable W weapon, long timeMs) {
        Entry<W> existing = find(kind, victimUuid, timeMs);
        if (existing != null) {
            if (existing.killer == null && killer != null) {
                existing.killer = killer;
                existing.killerMe = killerMe;
                existing.weapon = weapon;
            }
            return existing;
        }
        Entry<W> e = new Entry<>(kind, victimUuid, victim, victimMe, killer, killerMe, weapon, timeMs);
        entries.addFirst(e);
        while (entries.size() > KEPT) {
            entries.removeLast();
        }
        return e;
    }

    /** The kill was attributed later (to me, by the combat tracker): overrides a guessed or unknown killer. */
    public boolean setKiller(UUID victimUuid, String killer, boolean killerMe, @Nullable W weapon, long timeMs) {
        Entry<W> e = find(Kind.KILL, victimUuid, timeMs);
        if (e == null) {
            return false;
        }
        e.killer = killer;
        e.killerMe = killerMe;
        e.weapon = weapon;
        return true;
    }

    private @Nullable Entry<W> find(Kind kind, UUID victimUuid, long timeMs) {
        for (Entry<W> e : entries) {
            if (e.kind == kind && e.victimUuid.equals(victimUuid) && Math.abs(timeMs - e.timeMs) <= MERGE_MS) {
                return e;
            }
        }
        return null;
    }

    /** Rows still on screen, newest first, at most {@code max}. */
    public List<Entry<W>> live(long nowMs, long lifetimeMs, int max) {
        List<Entry<W>> out = new ArrayList<>();
        for (Entry<W> e : entries) {
            if (nowMs - e.timeMs < lifetimeMs && out.size() < max) {
                out.add(e);
            }
        }
        return out;
    }

    /** Opacity of a row: 1, fading to 0 over the last {@code fadeMs} of its lifetime. */
    public static float alpha(long ageMs, long lifetimeMs, long fadeMs) {
        if (ageMs >= lifetimeMs) {
            return 0f;
        }
        long left = lifetimeMs - ageMs;
        return fadeMs <= 0 || left >= fadeMs ? 1f : left / (float) fadeMs;
    }

    public void prune(long nowMs, long lifetimeMs) {
        Iterator<Entry<W>> it = entries.iterator();
        while (it.hasNext()) {
            if (nowMs - it.next().timeMs >= lifetimeMs + MERGE_MS) {
                it.remove();
            }
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }
}
