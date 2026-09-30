package dev.skirmish.module.pvp;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.module.pvp.mixin.CooldownInstanceAccessor;
import dev.skirmish.module.pvp.mixin.ItemCooldownsAccessor;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The local player's own item cooldowns (vanilla cooldown groups the server sent): pearl, golden apples, chorus,
 * wind charge, shield and HolyWorld's трапка / взрывная трапка / стан. Each is an item icon inside a countdown ring
 * with the seconds left below.
 */
final class CooldownHud extends HudBlock {
    private static final String L = "layout.pvp.";
    private static final List<Entry> SAMPLE = List.of(
            new Entry("minecraft:ender_pearl", new ItemStack(Items.ENDER_PEARL), 84f, 120f),
            new Entry("minecraft:golden_apple", new ItemStack(Items.GOLDEN_APPLE), 240f, 300f),
            new Entry("minecraft:nether_star", new ItemStack(Items.NETHER_STAR), 540f, 1200f));

    private final PvpModule module;
    private final Map<Identifier, ItemStack> icons = new HashMap<>();
    private List<Entry> current = List.of();
    private List<Entry> last = List.of();

    /** One cooldown group: icon, ticks left (with partial tick) and total ticks. */
    private record Entry(String group, ItemStack icon, float remaining, float total) {
    }

    CooldownHud(PvpModule module) {
        super("cooldowns", "skirmish.hud.element.cooldowns", new Placement(0.5f, 1f, 0.5f, 1f, 0, -110));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.cooldownHud.get();
    }

    @Override
    public boolean shown() {
        update(false);
        return !current.isEmpty();
    }

    @Override
    public boolean hasContent() {
        return !last.isEmpty();
    }

    @Override
    public void update(boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        current = player == null ? List.of() : read(player, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        if (!current.isEmpty()) {
            last = current;
        } else if (!last.isEmpty() && last.getFirst().remaining() > 0f) {
            // All ended: fade out on empty rings and 0.
            last = last.stream().map(e -> new Entry(e.group(), e.icon(), 0f, e.total())).toList();
        }
    }

    private List<Entry> read(LocalPlayer player, float partialTick) {
        ItemCooldowns cooldowns = player.getCooldowns();
        ItemCooldownsAccessor access = (ItemCooldownsAccessor) cooldowns;
        Map<Identifier, ?> active = access.skirmish$cooldowns();
        if (active.isEmpty()) {
            return List.of();
        }
        float now = access.skirmish$tickCount() + partialTick;
        List<Entry> entries = new ArrayList<>(active.size());
        for (Map.Entry<Identifier, ?> e : active.entrySet()) {
            CooldownInstanceAccessor instance = (CooldownInstanceAccessor) e.getValue();
            float total = instance.skirmish$endTime() - instance.skirmish$startTime();
            float remaining = instance.skirmish$endTime() - now;
            if (total > 0 && remaining > 0) {
                entries.add(new Entry(e.getKey().toString(), icon(e.getKey(), player, cooldowns), remaining, total));
            }
        }
        entries.sort(Comparator.comparing(Entry::group, CooldownOrder.BY_GROUP));
        return entries;
    }

    /** The group's item, or an inventory item using that (custom) cooldown group; empty when none is found. */
    private ItemStack icon(Identifier group, LocalPlayer player, ItemCooldowns cooldowns) {
        ItemStack cached = icons.get(group);
        if (cached != null) {
            return cached;
        }
        ItemStack icon = BuiltInRegistries.ITEM.getOptional(group).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (icon.isEmpty()) {
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && group.equals(cooldowns.getCooldownGroup(stack))) {
                    icon = stack.copyWithCount(1);
                    break;
                }
            }
            if (icon.isEmpty()) {
                return icon;
            }
        }
        icons.put(group, icon);
        return icon;
    }

    private List<Entry> entries(boolean preview) {
        return preview && current.isEmpty() || last.isEmpty() ? SAMPLE : last;
    }

    private static float tileHeight(Ui ui) {
        return ui.num(L + "cd_ring") + ui.num(L + "cd_label_gap") + ui.lineHeight("pvp_cd_seconds");
    }

    @Override
    public float width(Ui ui, boolean preview) {
        int n = entries(preview).size();
        return HudStyle.insetX(ui) * 2 + n * ui.num(L + "cd_ring") + (n - 1) * ui.num(L + "cd_gap");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return HudStyle.insetY(ui) * 2 + tileHeight(ui);
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        List<Entry> entries = entries(preview);
        HudStyle.panel(ui, x, y, width(ui, preview), height(ui, preview));
        float ring = ui.num(L + "cd_ring");
        float icon = ui.num(L + "cd_icon");
        float tx = x + HudStyle.insetX(ui);
        float ty = y + HudStyle.insetY(ui);
        for (Entry entry : entries) {
            float cx = tx + ring / 2f;
            float cy = ty + ring / 2f;
            Rings.countdown(ui, cx, cy, ring, ui.num(L + "cd_ring_width"), entry.remaining() / entry.total(),
                    ui.color("track"), ui.color("accent"));
            if (!entry.icon().isEmpty()) {
                var pose = ui.graphics().pose();
                pose.pushMatrix();
                pose.translate(Math.round(cx - icon / 2f), Math.round(cy - icon / 2f));
                pose.scale(icon / 16f, icon / 16f);
                ui.graphics().renderItem(entry.icon(), 0, 0);
                pose.popMatrix();
            }
            double seconds = CooldownOrder.shownSeconds(entry.remaining());
            String label = Ui.decimal(seconds, CooldownOrder.digits(seconds));
            ui.text("pvp_cd_seconds", label, cx - ui.textWidth("pvp_cd_seconds", label) / 2f,
                    ty + ring + ui.num(L + "cd_label_gap"));
            tx += ring + ui.num(L + "cd_gap");
        }
    }
}
