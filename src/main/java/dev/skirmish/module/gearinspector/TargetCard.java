package dev.skirmish.module.gearinspector;

import dev.skirmish.combat.EquipmentSnapshot;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Target card under the crosshair (mockup): face, name, HP with a smoothed bar, ping, and a row of the six
 * equipment slots with durability bars. With «Зачарования» on, a third block lists the enchantments.
 */
final class TargetCard extends HudBlock {
    private static final String L = "layout.hud.";
    private final GearInspectorModule module;
    private final Anim hp = new Anim("hp_smooth_ms");
    private @Nullable Player lastTarget;

    TargetCard(GearInspectorModule module) {
        super("target", "skirmish.hud.element.target", new Placement(0.5f, 0.5f, 0.5f, 0f, 0, 44));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    private @Nullable Player target() {
        Player target = module.tracker().shown();
        return target == null || target.isRemoved() ? null : target;
    }

    @Override
    public boolean shown() {
        return target() != null;
    }

    @Override
    public boolean hasContent() {
        return target() != null || lastTarget != null;
    }

    @Override
    public void update(boolean preview) {
        Player target = target();
        if (target != null) {
            if (target != lastTarget) {
                hp.snap(fraction(target));
            }
            lastTarget = target;
            hp.target(fraction(target));
        }
    }

    private static float fraction(Player p) {
        return p.getMaxHealth() <= 0 ? 0f : Math.max(0f, Math.min(1f, p.getHealth() / p.getMaxHealth()));
    }

    private List<GearReader.SlotView> slots(boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        Player target = preview && target() == null ? null : lastTarget;
        List<GearReader.SlotView> views = new ArrayList<>();
        ItemStack[] sample = {new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE),
                new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS),
                new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.TOTEM_OF_UNDYING)};
        int i = 0;
        for (EquipmentSlot slot : EquipmentSnapshot.SLOTS) {
            ItemStack stack = target == null ? sample[i] : target.getItemBySlot(slot);
            i++;
            if (slot.getType() == EquipmentSlot.Type.HAND && !module.showHands.get()) {
                continue;
            }
            if (stack.isEmpty() && !module.showEmptySlots.get()) {
                continue;
            }
            views.add(GearReader.read(slot, stack, module.assumeUndamaged.get(), mc.level));
        }
        return views;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "target_width");
    }

    private float cardHeight(Ui ui) {
        return ui.num("stroke.width") * 2 + ui.num(L + "target_pad") * 2 + ui.num(L + "face")
                + ui.num(L + "target_gap") + ui.num(L + "hp_bar");
    }

    private float gearHeight(Ui ui) {
        return ui.num("stroke.width") * 2 + ui.num(L + "gear_row_pad_y") * 2 + ui.num(L + "gear_icon")
                + ui.num(L + "gear_slot_gap") + ui.num(L + "gear_bar_height");
    }

    private boolean sample(boolean preview) {
        return preview && target() == null || lastTarget == null;
    }

    private List<String[]> details(Ui ui, List<GearReader.SlotView> slots, boolean preview) {
        List<String[]> lines = new ArrayList<>();
        if (!module.showEnchantments.get() || sample(preview)) {
            return lines;
        }
        float textW = width(ui, false) - HudStyle.insetX(ui) * 2;
        for (GearReader.SlotView view : slots) {
            if (view.stack().isEmpty()) {
                continue;
            }
            String value = view.durability().percentText();
            if (module.showAbsolute.get() && view.durability().kind() == Durability.Kind.PERCENT) {
                value += " " + view.durability().remaining() + "/" + view.durability().max();
            }
            lines.add(new String[]{"row_value", ui.ellipsize("row_value", view.stack().getHoverName().getString(),
                    textW - ui.textWidth("row", value) - 8), value});
            String enchants = switch (view.enchantStatus()) {
                case LISTED -> String.join(", ", view.enchantments().stream().map(Component::getString).toList());
                case NO_DATA -> module.showMissingEnchantments.get() ? Ui.tr("skirmish.gearinspector.enchantments_no_data") : "";
                case GLINT_ONLY -> module.showMissingEnchantments.get() ? Ui.tr("skirmish.gearinspector.enchantments_glint_only") : "";
                case NOT_APPLICABLE -> "";
            };
            for (String line : enchants.isEmpty() ? List.<String>of() : ui.wrap("row", enchants, textW)) {
                lines.add(new String[]{"row", line, ""});
            }
        }
        return lines;
    }

    private float detailsHeight(Ui ui, List<String[]> lines) {
        if (lines.isEmpty()) {
            return 0f;
        }
        float h = HudStyle.insetY(ui) * 2;
        for (String[] line : lines) {
            h += ui.lineHeight(line[0]);
        }
        return h + ui.num(L + "target_stack_gap");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        List<GearReader.SlotView> slots = slots(preview);
        float h = cardHeight(ui);
        if (!slots.isEmpty()) {
            h += ui.num(L + "target_stack_gap") + gearHeight(ui);
        }
        return h + detailsHeight(ui, details(ui, slots, preview));
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        boolean sample = sample(preview);
        Player target = sample ? null : lastTarget;
        float stroke = ui.num("stroke.width");
        float w = width(ui, preview);
        float ch = cardHeight(ui);
        HudStyle.panel(ui, x, y, w, ch);
        float pad = ui.num(L + "target_pad");
        float cx = x + stroke + pad;
        float cy = y + stroke + pad;
        float cw = w - (stroke + pad) * 2;

        // Face (skin head with hat layer), rounded by masking the corners with the panel color.
        float face = ui.num(L + "face");
        PlayerInfo info = mc.getConnection() == null ? null
                : mc.getConnection().getPlayerInfo(target != null ? target.getUUID() : mc.player.getUUID());
        if (info != null) {
            PlayerFaceRenderer.draw(ui.graphics(), info.getSkin(), Math.round(cx), Math.round(cy), Math.round(face));
            ui.cornerMask(Math.round(cx), Math.round(cy), face, face, ui.theme().radius("face"), ui.color("panel"));
        } else {
            ui.rect(cx, cy, face, face, ui.theme().radius("face"), ui.color("slot_empty"));
        }

        // Ping pill on the right.
        String ping = Ui.tr("skirmish.hud.target.ping", sample ? 42 : info == null ? 0 : info.getLatency());
        float ppx = ui.num(L + "ping_pad_x");
        float ppy = ui.num(L + "ping_pad_y");
        float pw = ui.textWidth("target_ping", ping) + ppx * 2 + stroke * 2;
        float ph = ui.lineHeight("target_ping") + ppy * 2 + stroke * 2;
        float px = cx + cw - pw;
        ui.border(px, cy + (face - ph) / 2f, pw, ph, ph / 2f, stroke, ui.color("stroke_08"));
        ui.text("target_ping", ping, px + stroke + ppx, cy + (face - ph) / 2f + stroke + ppy);

        // Name and HP.
        float tx = cx + face + ui.num(L + "target_header_gap");
        float textW = px - ui.num(L + "target_header_gap") - tx;
        String name = sample ? "GFk31AK" : target.getName().getString();
        float textH = ui.lineHeight("target_name") + ui.num(L + "hp_text_gap") + ui.lineHeight("target_hp");
        float ty = cy + (face - textH) / 2f;
        ui.text("target_name", ui.ellipsize("target_name", name, textW), tx, ty);
        ty += ui.lineHeight("target_name") + ui.num(L + "hp_text_gap");
        float health = sample ? 16f : target.getHealth();
        float max = sample ? 20f : target.getMaxHealth();
        float hx = ui.text("target_hp", "HP ", tx, ty);
        hx = ui.text("target_hp_value", Ui.decimal(health, 1), hx, ty);
        ui.text("target_hp_max", " / " + Ui.decimal(max, 0), hx, ty);

        // HP bar.
        float by = cy + face + ui.num(L + "target_gap");
        float bar = ui.num(L + "hp_bar");
        ui.rect(cx, by, cw, bar, bar / 2f, ui.color("hp_track"));
        float f = sample ? 0.8f : hp.value();
        ui.rect(cx, by, cw * f, bar, bar / 2f, ui.color("accent"));

        // Gear row, centered under the card.
        List<GearReader.SlotView> slots = slots(preview);
        float gy = y + ch;
        if (!slots.isEmpty()) {
            gy += ui.num(L + "target_stack_gap");
            float slotW = ui.num(L + "gear_slot_width");
            float gap = ui.num(L + "gear_row_gap");
            float rpx = ui.num(L + "gear_row_pad_x");
            float rpy = ui.num(L + "gear_row_pad_y");
            float gw = stroke * 2 + rpx * 2 + slots.size() * slotW + (slots.size() - 1) * gap;
            float gh = gearHeight(ui);
            float gx = Math.round(x + (w - gw) / 2f);
            ui.box(gx, gy, gw, gh, ui.theme().radius("gear_row"), ui.color("panel"), ui.color("stroke"));
            float sx = gx + stroke + rpx;
            float icon = ui.num(L + "gear_icon");
            String[] sampleBars = {"good", "good", "warn", "good", "bad", "slot_empty"};
            int index = 0;
            for (GearReader.SlotView view : slots) {
                String barColor = sample ? sampleBars[Math.min(index++, sampleBars.length - 1)] : durabilityColor(view);
                float ix = Math.round(sx + (slotW - icon) / 2f);
                float iy = gy + stroke + rpy;
                if (view.stack().isEmpty()) {
                    ui.rect(ix, iy, icon, icon, ui.theme().radius("slot"), ui.color("slot_empty"));
                } else {
                    var pose = ui.graphics().pose();
                    pose.pushMatrix();
                    pose.translate(ix, iy);
                    pose.scale(icon / 16f, icon / 16f);
                    ui.graphics().renderItem(view.stack(), 0, 0);
                    pose.popMatrix();
                }
                float barH = ui.num(L + "gear_bar_height");
                ui.rect(ix, iy + icon + ui.num(L + "gear_slot_gap"), icon, barH, barH, ui.color(barColor));
                sx += slotW + gap;
            }
            gy += gh;
        }

        List<String[]> lines = details(ui, slots, preview);
        if (!lines.isEmpty()) {
            gy += ui.num(L + "target_stack_gap");
            HudStyle.panel(ui, x, gy, w, detailsHeight(ui, lines) - ui.num(L + "target_stack_gap"));
            float lx = x + HudStyle.insetX(ui);
            float ly = gy + HudStyle.insetY(ui);
            for (String[] line : lines) {
                ui.text(line[0], line[1], lx, ly);
                if (!line[2].isEmpty()) {
                    ui.text("row", line[2], x + w - HudStyle.insetX(ui) - ui.textWidth("row", line[2]), ly);
                }
                ly += ui.lineHeight(line[0]);
            }
        }
    }

    private static String durabilityColor(GearReader.SlotView view) {
        Durability d = view.durability();
        return switch (d.kind()) {
            case PERCENT -> d.fraction() > 0.5 ? "good" : d.fraction() > 0.25 ? "warn" : "bad";
            case ASSUMED_FULL, UNBREAKABLE -> "good";
            case NOT_DAMAGEABLE, NO_DATA -> "slot_empty";
        };
    }
}
