package dev.skirmish.module.survival;

import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Set;

/**
 * «Предупреждения выживания»: a big centered warning (and an optional sound) when my health is low, there is no
 * totem in my off hand during PvP, a worn armor piece is almost broken, food is low, or pearls / golden apples run
 * out. Reads only my own health, food and inventory; never moves, uses or clicks anything.
 */
public final class SurvivalAlertsModule extends Module {
    public static final String ID = "survival_alerts";
    /** Sound at most this often per warning kind, even when it flickers on and off. */
    private static final long SOUND_REPEAT_MS = 4000;

    final BoolSetting lowHp = add(new BoolSetting("low_hp", true));
    final NumberSetting hpThreshold = (NumberSetting) add(new NumberSetting("hp_threshold", 8, 1, 19, 1).unit(" HP"))
            .under(lowHp).visibleWhen(lowHp::get);
    final BoolSetting noTotem = add(new BoolSetting("no_totem", true));
    final BoolSetting armor = add(new BoolSetting("armor", true));
    final NumberSetting armorPercent = (NumberSetting) add(new NumberSetting("armor_percent", 10, 1, 50, 1).unit("%"))
            .under(armor).visibleWhen(armor::get);
    final BoolSetting food = add(new BoolSetting("food", false));
    final NumberSetting foodThreshold = (NumberSetting) add(new NumberSetting("food_threshold", 6, 1, 19, 1))
            .under(food).visibleWhen(food::get);
    final BoolSetting pearls = add(new BoolSetting("pearls", false));
    final NumberSetting pearlThreshold = (NumberSetting) add(new NumberSetting("pearl_threshold", 2, 0, 16, 1))
            .under(pearls).visibleWhen(pearls::get);
    final BoolSetting gapples = add(new BoolSetting("gapples", false));
    final NumberSetting gappleThreshold = (NumberSetting) add(new NumberSetting("gapple_threshold", 2, 0, 16, 1))
            .under(gapples).visibleWhen(gapples::get);
    final BoolSetting suppliesPvpOnly = add(new BoolSetting("supplies_pvp_only", true));
    final BoolSetting flash = add(new BoolSetting("flash", true));
    final BoolSetting sound = add(new BoolSetting("sound", true));
    final NumberSetting volume = (NumberSetting) add(new NumberSetting("volume", 60, 10, 100, 10).unit("%"))
            .under(sound).visibleWhen(sound::get);

    private Set<SurvivalRules.Alert> active = EnumSet.noneOf(SurvivalRules.Alert.class);
    private final long[] lastSound = new long[SurvivalRules.Alert.values().length];
    private SurvivalRules.Snapshot snapshot = new SurvivalRules.Snapshot(20f, 20, false, true, 1.0, 16, 16);

    public SurvivalAlertsModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.COMBAT;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        Hud.get().register(new SurvivalBanner(this));
    }

    @Override
    protected void onDisable() {
        active = EnumSet.noneOf(SurvivalRules.Alert.class);
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !player.isAlive() || player.isSpectator() || player.isCreative()) {
            active = EnumSet.noneOf(SurvivalRules.Alert.class);
            return;
        }
        snapshot = new SurvivalRules.Snapshot(player.getHealth(), player.getFoodData().getFoodLevel(), PvpState.inPvp(),
                player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), Supplies.worstArmor(player),
                Supplies.count(player, Items.ENDER_PEARL),
                Supplies.count(player, Items.GOLDEN_APPLE) + Supplies.count(player, Items.ENCHANTED_GOLDEN_APPLE));
        Set<SurvivalRules.Alert> next = SurvivalRules.evaluate(snapshot, config(), active);
        Set<SurvivalRules.Alert> started = SurvivalRules.started(active, next);
        if (!started.isEmpty()) {
            log("warnings " + next + " (new " + started + ")");
            playSound(mc, started);
        }
        active = next;
    }

    private SurvivalRules.Config config() {
        return new SurvivalRules.Config(lowHp.get(), hpThreshold.getFloat(), noTotem.get(), armor.get(),
                armorPercent.get() / 100.0, food.get(), foodThreshold.getInt(), pearls.get(), pearlThreshold.getInt(),
                gapples.get(), gappleThreshold.getInt(), suppliesPvpOnly.get());
    }

    private void playSound(Minecraft mc, Set<SurvivalRules.Alert> started) {
        if (!sound.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean play = false;
        boolean critical = false;
        for (SurvivalRules.Alert alert : started) {
            if (now - lastSound[alert.ordinal()] >= SOUND_REPEAT_MS) {
                lastSound[alert.ordinal()] = now;
                play = true;
                critical |= alert.critical();
            }
        }
        if (play) {
            float vol = volume.getFloat() / 100f;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(critical ? SoundEvents.NOTE_BLOCK_BELL.value()
                    : SoundEvents.NOTE_BLOCK_PLING.value(), critical ? 1.4f : 1.0f, vol));
        }
    }

    Set<SurvivalRules.Alert> active() {
        return active;
    }

    SurvivalRules.Snapshot snapshot() {
        return snapshot;
    }
}
