package dev.skirmish.module.killcam.library;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Why a replay was saved; shown as the result badge (Победа / Смерть / Клип). */
public enum ReplayKind {
    /** I killed someone: saved on {@code CombatListener.onKill}. */
    KILL,
    /** I died: saved once the recorder froze after the death. */
    DEATH,
    /** Saved on demand with the «Сохранить момент» key. */
    CLIP;

    /** Lower-case id used in files and lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable ReplayKind parse(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (ReplayKind kind : values()) {
            if (kind.id().equalsIgnoreCase(id)) {
                return kind;
            }
        }
        return null;
    }
}
