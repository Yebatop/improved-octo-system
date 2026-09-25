package dev.skirmish.module.analytics;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Damage type ids as the combat tracker reports them ({@code minecraft:player_attack}, or {@code minecraft:generic
 * (inferred)} when the attacker was inferred) to short labels. Pure Java, unit tested.
 */
public final class DamageTypes {
    /** Suffix the combat tracker appends when it inferred the attacker. */
    public static final String INFERRED_SUFFIX = " (inferred)";

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("player_attack", "melee"), Map.entry("mob_attack", "mob"), Map.entry("mob_attack_no_aggro", "mob"),
            Map.entry("generic", "hit"), Map.entry("arrow", "arrow"), Map.entry("trident", "trident"),
            Map.entry("mob_projectile", "projectile"), Map.entry("spit", "projectile"), Map.entry("wind_charge", "projectile"),
            Map.entry("fireworks", "fireworks"), Map.entry("fall", "fall"), Map.entry("ender_pearl", "fall"),
            Map.entry("fly_into_wall", "fall"), Map.entry("stalagmite", "fall"), Map.entry("lava", "lava"),
            Map.entry("in_fire", "fire"), Map.entry("on_fire", "fire"), Map.entry("campfire", "fire"), Map.entry("hot_floor", "fire"),
            Map.entry("unattributed_fireball", "fire"), Map.entry("fireball", "fire"),
            Map.entry("explosion", "explosion"), Map.entry("player_explosion", "explosion"), Map.entry("bad_respawn_point", "explosion"),
            Map.entry("magic", "magic"), Map.entry("indirect_magic", "magic"), Map.entry("thorns", "thorns"),
            Map.entry("wither", "wither"), Map.entry("wither_skull", "wither"), Map.entry("drown", "drown"),
            Map.entry("starve", "starve"), Map.entry("out_of_world", "void"), Map.entry("outside_border", "void"),
            Map.entry("in_wall", "suffocation"), Map.entry("cramming", "suffocation"), Map.entry("cactus", "cactus"),
            Map.entry("sweet_berry_bush", "cactus"), Map.entry("freeze", "freeze"), Map.entry("lightning_bolt", "lightning"),
            Map.entry("falling_anvil", "falling_block"), Map.entry("falling_block", "falling_block"),
            Map.entry("falling_stalactite", "falling_block"), Map.entry("sonic_boom", "magic"), Map.entry("dragon_breath", "magic"),
            Map.entry("mace_smash", "melee"), Map.entry("sting", "mob"), Map.entry("thrown", "projectile"));

    /** Types that are the world's doing, not someone's hit: they never break my combo. */
    private static final java.util.Set<String> ENVIRONMENT = java.util.Set.of("fall", "lava", "fire", "drown", "starve", "void",
            "suffocation", "cactus", "freeze", "lightning", "falling_block", "wither", "magic");

    private DamageTypes() {
    }

    /** The id without the inference suffix. */
    public static String base(String type) {
        return type.endsWith(INFERRED_SUFFIX) ? type.substring(0, type.length() - INFERRED_SUFFIX.length()) : type;
    }

    public static boolean inferred(String type) {
        return type.endsWith(INFERRED_SUFFIX);
    }

    /** Path of the id: {@code minecraft:arrow} → {@code arrow}. */
    public static String path(String type) {
        String b = base(type);
        int colon = b.indexOf(':');
        return colon >= 0 ? b.substring(colon + 1) : b;
    }

    /** Label id for {@code skirmish.analytics.damage.<label>}, null for unknown types (shown by their path). */
    public static @Nullable String label(String type) {
        return LABELS.get(path(type));
    }

    /**
     * Whether damage of this type on me counts as "being hit" (breaks my combo): anything with an attacker, and any
     * attack-like type without one (HolyWorld sends every hit as {@code generic} without a source).
     */
    public static boolean isHit(String type, boolean hasAttacker) {
        if (hasAttacker) {
            return true;
        }
        String label = label(type);
        return label == null || !ENVIRONMENT.contains(label);
    }
}
