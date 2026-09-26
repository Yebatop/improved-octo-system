package dev.skirmish.module.nametag;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.survival.PvpState;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * «Nametag HP»: other players' health from what the server already syncs ({@code LivingEntity#getHealth}).
 * <ul>
 *     <li><b>Skirmish</b> (default): a plate over the head in the client's style ({@link HpPlates}) with the face,
 *     nick, HP and a bar with a damage trail. Only for players in plain line of sight; it replaces their vanilla
 *     nametag while shown. Invisible players get one too when «Невидимые» is on (allowed by HolyWorld's rules,
 *     Feature Control id {@code hp_invisible}).</li>
 *     <li><b>Vanilla</b>: an extra nametag line and a small bar, drawn through the vanilla nametag path.</li>
 * </ul>
 * Render-only.
 */
public final class NametagHpModule extends Module {
    public static final String ID = "nametag_hp";
    private static @Nullable NametagHpModule instance;

    /** Plate in the client's style, or a vanilla-looking nametag line. */
    public enum Style {
        SKIRMISH, VANILLA
    }

    /** Where the HP goes relative to the nick. */
    public enum Position {
        ABOVE, BELOW, INLINE
    }

    final EnumSetting<Style> style = add(new EnumSetting<>("style", Style.SKIRMISH));
    final EnumSetting<NametagPolicy.Show> show = add(new EnumSetting<>("show", NametagPolicy.Show.ALWAYS));
    final NumberSetting distance = (NumberSetting) add(new NumberSetting("distance", 32, 8, 64, 4).unit("m"))
            .visibleWhen(() -> style.get() == Style.SKIRMISH);
    final NumberSetting scale = (NumberSetting) add(new NumberSetting("scale", 100, 60, 160, 10).unit("%"))
            .visibleWhen(() -> style.get() == Style.SKIRMISH);
    final BoolSetting face = (BoolSetting) add(new BoolSetting("face", true)).visibleWhen(() -> style.get() == Style.SKIRMISH);
    final BoolSetting invisible = (BoolSetting) add(new BoolSetting("invisible", true)).feature("hp_invisible")
            .visibleWhen(() -> style.get() == Style.SKIRMISH);
    final EnumSetting<HpText.Units> units = add(new EnumSetting<>("units", HpText.Units.HP));
    final EnumSetting<Position> position = (EnumSetting<Position>) add(new EnumSetting<>("position", Position.ABOVE))
            .visibleWhen(() -> style.get() == Style.VANILLA);
    final BoolSetting absorption = add(new BoolSetting("absorption", true));
    final BoolSetting bar = (BoolSetting) add(new BoolSetting("bar", true))
            .visibleWhen(() -> style.get() == Style.VANILLA && position.get() != Position.INLINE);

    /** PvP state sampled once per tick instead of per nametag per frame. */
    private boolean inPvp;
    private final HpPlates plates = new HpPlates(this);

    public NametagHpModule() {
        super(ID, true);
        instance = this;
    }

    static @Nullable NametagHpModule instance() {
        return instance;
    }

    @Override
    public Category category() {
        return Category.VISUAL;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("skirmish", "hp_plates"), plates);
    }

    @Override
    public void tick() {
        inPvp = show.get() == NametagPolicy.Show.COMBAT && PvpState.inPvp();
        plates.tick();
    }

    @Override
    protected void onDisable() {
        inPvp = false;
        plates.clear();
    }

    boolean skirmishStyle() {
        return style.get() == Style.SKIRMISH;
    }

    /** Whether a Skirmish plate stands in for this player's vanilla nametag this tick. */
    public static boolean replacesNameTag(int entityId) {
        NametagHpModule module = instance;
        return module != null && module.isEnabled() && module.skirmishStyle() && module.plates.shows(entityId);
    }

    /** Plain line of sight from {@code from} to the target's head, middle or feet (the plates' own test). */
    public static boolean inPlainSight(net.minecraft.client.Minecraft mc, net.minecraft.world.phys.Vec3 from, net.minecraft.world.entity.Entity target) {
        return HpPlates.lineOfSight(mc, from, target);
    }

    boolean inPvp() {
        return inPvp;
    }
}
