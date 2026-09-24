package dev.skirmish.module.gearinspector;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.module.Module;
import dev.skirmish.module.gearinspector.holy.HolyProfile;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * GearInspector: armor, hands, enchantments and durability of the player under the crosshair (own raycast up to
 * {@link #maxDistance}) or of a player locked with {@link SkirmishKeys#GEARINSPECTOR_LOCK}. Read-only: it shows the
 * equipment stacks exactly as the client received them and says "no data" where the server sent nothing.
 */
public final class GearInspectorModule extends Module {
    public static final String ID = "gearinspector";

    final NumberSetting maxDistance = add(new NumberSetting("max_distance", 24, 5, 64, 1).unit(" m"));
    final NumberSetting lockDistance = add(new NumberSetting("lock_distance", 64, 5, 128, 1).unit(" m"));
    final NumberSetting linger = add(new NumberSetting("linger", 1.0, 0, 5, 0.5).unit(" s"));
    final NumberSetting hitboxMargin = add(new NumberSetting("hitbox_margin", 0.2, 0, 1, 0.1).unit(" m"));
    final BoolSetting throughWalls = (BoolSetting) add(new BoolSetting("through_walls", false)).feature("through_walls");
    final BoolSetting showHands = add(new BoolSetting("show_hands", true));
    final BoolSetting showEmptySlots = add(new BoolSetting("show_empty_slots", true));
    final BoolSetting showEnchantments = add(new BoolSetting("show_enchantments", false));
    final BoolSetting showMissingEnchantments = add(new BoolSetting("show_missing_enchantments", true));
    final BoolSetting showAbsolute = add(new BoolSetting("show_absolute", false));
    final BoolSetting assumeUndamaged = add(new BoolSetting("assume_undamaged", false));
    final BoolSetting lockMessages = add(new BoolSetting("lock_messages", true));
    /** HolyWorld item knowledge: donor tiers, lore enchantments, off-hand talismans, Lite armour wear. */
    final EnumSetting<HolyProfile> holyProfile = (EnumSetting<HolyProfile>) add(new EnumSetting<>("holy_profile", HolyProfile.AUTO))
            .feature("holy_gear_profile");
    final BoolSetting holyHitsLeft = add(new BoolSetting("holy_hits_left", true));

    private final TargetTracker tracker = new TargetTracker(this);

    public GearInspectorModule() {
        super(ID, true);
        showMissingEnchantments.visibleWhen(showEnchantments::get);
        showAbsolute.visibleWhen(showEnchantments::get);
        holyHitsLeft.visibleWhen(() -> showEnchantments.get() && holyProfile.get() == HolyProfile.AUTO && !holyProfile.isBlocked());
    }

    @Override
    public void onInitialize() {
        dev.skirmish.hud.Hud.get().register(new TargetCard(this));
    }

    @Override
    protected void onEnable() {
        KeyMapping key = lockKey();
        while (key.consumeClick()) {
            // Presses made while the module was off are dropped instead of toggling the lock on enable.
        }
    }

    @Override
    protected void onDisable() {
        tracker.reset("module disabled");
    }

    @Override
    public void tick() {
        tracker.tick(Minecraft.getInstance());
    }

    TargetTracker tracker() {
        return tracker;
    }

    /** «Профиль HolyWorld» is AUTO, not blocked by Feature Control, and the client is on HolyWorld. */
    boolean holyActive() {
        return holyProfile.get() == HolyProfile.AUTO && !holyProfile.isBlocked() && HolyWorld.isConnected();
    }

    KeyMapping lockKey() {
        return SkirmishKeys.GEARINSPECTOR_LOCK;
    }
}
