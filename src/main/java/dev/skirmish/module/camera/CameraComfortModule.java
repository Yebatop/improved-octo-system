package dev.skirmish.module.camera;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.util.OptionLedger;
import dev.skirmish.util.VanillaOverrides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * One panel for vanilla's camera accessibility options: View Bobbing, Damage Tilt, FOV Effects, Distortion Effects
 * and Hide Lightning Flashes. While enabled the module writes those vanilla options (the same as changing them in
 * Options → Accessibility / Video) and puts the user's own values back when disabled. No mixin.
 * The "Darkness Pulsing" option is deliberately not offered: lowering it would weaken the Darkness effect.
 */
public final class CameraComfortModule extends Module {
    public static final String ID = "camera_comfort";

    final BoolSetting viewBobbing = add(new BoolSetting("view_bobbing", false));
    final NumberSetting damageTilt = add(new NumberSetting("damage_tilt", 0, 0, 100, 5).unit("%"));
    final NumberSetting fovEffects = add(new NumberSetting("fov_effects", 0, 0, 100, 5).unit("%"));
    final NumberSetting distortion = add(new NumberSetting("distortion", 100, 0, 100, 5).unit("%"));
    final BoolSetting hideLightning = add(new BoolSetting("hide_lightning", false));
    private final OptionLedger.Store saved = add(new OptionLedger.Store("saved_vanilla"));

    private final VanillaOverrides overrides;
    private boolean dirty;

    public CameraComfortModule() {
        super(ID, false);
        overrides = new VanillaOverrides(new OptionLedger(saved))
                .bool("bobView", Options::bobView, viewBobbing::get)
                .number("damageTiltStrength", Options::damageTiltStrength, () -> damageTilt.get() / 100.0)
                .number("fovEffectScale", Options::fovEffectScale, () -> fovEffects.get() / 100.0)
                .number("screenEffectScale", Options::screenEffectScale, () -> distortion.get() / 100.0)
                .bool("hideLightningFlash", Options::hideLightningFlash, hideLightning::get);
        viewBobbing.onChange(v -> dirty = true);
        damageTilt.onChange(v -> dirty = true);
        fovEffects.onChange(v -> dirty = true);
        distortion.onChange(v -> dirty = true);
        hideLightning.onChange(v -> dirty = true);
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
    protected void onEnable() {
        dirty = true;
    }

    @Override
    protected void onDisable() {
        dirty = false;
        Options options = Minecraft.getInstance().options;
        if (options != null && overrides.restore(options)) {
            options.save();
            log("restored the user's camera options");
        }
    }

    @Override
    public void tick() {
        Options options = Minecraft.getInstance().options;
        if (dirty && options != null) {
            dirty = false;
            if (overrides.apply(options)) {
                options.save();
                log("camera options written: bob=%s tilt=%s fov=%s distortion=%s lightning=%s", viewBobbing.get(),
                        damageTilt.get(), fovEffects.get(), distortion.get(), hideLightning.get());
            }
        }
    }
}
