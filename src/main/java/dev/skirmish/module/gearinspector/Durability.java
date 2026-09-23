package dev.skirmish.module.gearinspector;

/**
 * Durability of one equipment stack as far as the client knows it. Pure Java; the "no data" decisions live here.
 *
 * @param remaining meaningful for {@link Kind#PERCENT} and {@link Kind#ASSUMED_FULL}
 * @param max       meaningful for {@link Kind#PERCENT} and {@link Kind#ASSUMED_FULL}
 */
public record Durability(Kind kind, Reason reason, int remaining, int max) {
    public enum Kind {
        /** The server sent both max_damage and damage: real percentage. */
        PERCENT,
        /** Damageable item without a damage value in the patch, shown as "100%?" only when the user opted in. */
        ASSUMED_FULL,
        /** The stack carries the unbreakable component. */
        UNBREAKABLE,
        /** The item type has no durability at all (no max_damage by default or on the stack). */
        NOT_DAMAGEABLE,
        /** Nothing trustworthy was received; {@link #reason} says why. */
        NO_DATA
    }

    public enum Reason {
        NONE,
        /** The client got an empty stack: nothing equipped, or the server does not send this slot. */
        EMPTY_SLOT,
        /** The item is damageable by default but its max_damage was removed in the received stack. */
        MAX_DAMAGE_REMOVED,
        /** max_damage is present but the damage component was removed in the received stack. */
        DAMAGE_REMOVED,
        /** The server put no damage value into the stack (vanilla does this for undamaged items, so do servers hiding durability). */
        DAMAGE_NOT_SENT,
        /** max_damage is zero or negative, which vanilla never sends. */
        INVALID_MAX_DAMAGE
    }

    public static Durability classify(DamageFacts facts, boolean assumeUndamaged) {
        if (facts.empty()) {
            return noData(Reason.EMPTY_SLOT);
        }
        if (facts.unbreakable()) {
            return new Durability(Kind.UNBREAKABLE, Reason.NONE, 0, 0);
        }
        Integer max = facts.maxDamage();
        if (max == null) {
            return facts.prototypeHasMaxDamage()
                    ? noData(Reason.MAX_DAMAGE_REMOVED)
                    : new Durability(Kind.NOT_DAMAGEABLE, Reason.NONE, 0, 0);
        }
        if (max <= 0) {
            return noData(Reason.INVALID_MAX_DAMAGE);
        }
        Integer damage = facts.damage();
        if (damage == null) {
            return noData(Reason.DAMAGE_REMOVED);
        }
        if (!facts.damageSent()) {
            return assumeUndamaged
                    ? new Durability(Kind.ASSUMED_FULL, Reason.DAMAGE_NOT_SENT, max, max)
                    : noData(Reason.DAMAGE_NOT_SENT);
        }
        int remaining = Math.max(0, Math.min(max, max - damage));
        return new Durability(Kind.PERCENT, Reason.NONE, remaining, max);
    }

    private static Durability noData(Reason reason) {
        return new Durability(Kind.NO_DATA, reason, 0, 0);
    }

    public boolean hasValue() {
        return kind == Kind.PERCENT || kind == Kind.ASSUMED_FULL;
    }

    public double fraction() {
        return hasValue() && max > 0 ? (double) remaining / max : 0;
    }

    /** "87%", "<1%"; "100%?" for {@link Kind#ASSUMED_FULL}; empty for kinds without a number. */
    public String percentText() {
        return switch (kind) {
            case PERCENT -> GearFormat.percentText(remaining, max);
            case ASSUMED_FULL -> "100%?";
            default -> "";
        };
    }

    /** ARGB text color of the durability value. */
    public int color() {
        return switch (kind) {
            case PERCENT -> GearFormat.gradient(fraction());
            case ASSUMED_FULL -> GearFormat.ASSUMED;
            case UNBREAKABLE -> GearFormat.UNBREAKABLE;
            case NOT_DAMAGEABLE -> GearFormat.NOT_DAMAGEABLE;
            case NO_DATA -> GearFormat.NO_DATA;
        };
    }

    /** English explanation for debug.log. */
    public String describe() {
        return switch (kind) {
            case PERCENT -> percentText() + " (damage " + (max - remaining) + " of " + max + ", sent by the server)";
            case ASSUMED_FULL -> "100%? (no damage value sent; assumed undamaged by setting, max_damage " + max + ")";
            case UNBREAKABLE -> "unbreakable (unbreakable component present)";
            case NOT_DAMAGEABLE -> "not damageable (no max_damage on the item type or the stack)";
            case NO_DATA -> "NO DATA: " + switch (reason) {
                case EMPTY_SLOT -> "empty stack (nothing equipped, or the server does not send this slot)";
                case MAX_DAMAGE_REMOVED -> "max_damage removed from a damageable item (stripped by the server)";
                case DAMAGE_REMOVED -> "damage component removed (stripped by the server)";
                case DAMAGE_NOT_SENT -> "damage not in the stack's component patch (vanilla omits it for undamaged items;"
                        + " servers hiding durability omit it too)";
                case INVALID_MAX_DAMAGE -> "max_damage <= 0";
                case NONE -> "unknown";
            };
        };
    }
}
