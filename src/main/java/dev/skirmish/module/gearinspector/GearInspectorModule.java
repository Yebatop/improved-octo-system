package dev.skirmish.module.gearinspector;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

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
    final BoolSetting throughWalls = add(new BoolSetting("through_walls", false));
    final EnumSetting<Anchor> anchor = add(new EnumSetting<>("anchor", Anchor.TOP_LEFT));
    final NumberSetting offsetX = add(new NumberSetting("offset_x", 4, -400, 400, 1));
    final NumberSetting offsetY = add(new NumberSetting("offset_y", 4, -400, 400, 1));
    final NumberSetting scale = add(new NumberSetting("scale", 1.0, 0.5, 2.0, 0.1));
    final NumberSetting panelWidth = add(new NumberSetting("panel_width", 180, 120, 320, 10));
    final NumberSetting backgroundOpacity = add(new NumberSetting("background_opacity", 50, 0, 100, 5).unit("%"));
    final BoolSetting showHands = add(new BoolSetting("show_hands", true));
    final BoolSetting showEmptySlots = add(new BoolSetting("show_empty_slots", true));
    final BoolSetting showItemNames = add(new BoolSetting("show_item_names", true));
    final BoolSetting showEnchantments = add(new BoolSetting("show_enchantments", true));
    final BoolSetting showMissingEnchantments = add(new BoolSetting("show_missing_enchantments", true));
    final BoolSetting showAbsolute = add(new BoolSetting("show_absolute", false));
    final BoolSetting assumeUndamaged = add(new BoolSetting("assume_undamaged", false));
    final BoolSetting lockMessages = add(new BoolSetting("lock_messages", true));

    private final TargetTracker tracker = new TargetTracker(this);

    public GearInspectorModule() {
        super(ID, true);
        showMissingEnchantments.visibleWhen(showEnchantments::get);
    }

    @Override
    public void onInitialize() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("skirmish", ID),
                new GearHud(this));
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

    KeyMapping lockKey() {
        return SkirmishKeys.GEARINSPECTOR_LOCK;
    }
}
