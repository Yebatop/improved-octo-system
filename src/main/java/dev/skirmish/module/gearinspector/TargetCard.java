package dev.skirmish.module.gearinspector;

import dev.skirmish.combat.EquipmentSnapshot;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.module.gearinspector.holy.CustomEnchant;
import dev.skirmish.module.gearinspector.holy.DonorTier;
import dev.skirmish.module.gearinspector.holy.HolyGear;
import dev.skirmish.module.gearinspector.holy.HolyText;
import dev.skirmish.module.gearinspector.holy.LiteArmorWear;
import dev.skirmish.module.gearinspector.holy.Talisman;
import dev.skirmish.module.gearinspector.holy.TierSummary;
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
import java.util.Locale;

/**
 * Target card under the crosshair (mockup): face, name, HP with a smoothed bar, ping, and a row of the six
 * equipment slots with durability bars. With «Зачарования» on, a third block lists the enchantments.
 * <p>
 * With the HolyWorld profile active: a donor tier chip beside the ping, and in the list the tier line, lore
 * enchantments next to vanilla ones, over-cap vanilla levels marked, off-hand sphere/talisman stats and Lite's
 * estimated hits left for armour. With nothing HolyWorld-specific on the target the card looks exactly as before.
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
            views.add(GearReader.read(slot, stack, module.assumeUndamaged.get(), mc.level, target != null && module.holyActive()));
        }
        return views;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        TierSummary tier = sample(preview) ? null : armourTier(slots(preview));
        // The tier chip gets its own room so the name and HP keep the mockup's width.
        return ui.num(L + "target_width") + (tier == null ? 0f : tierChipWidth(ui, tier) + ui.num(L + "holy_chip_gap"));
    }

    private static String tierChipText(TierSummary tier) {
        return Ui.tr("skirmish.gearinspector.holy.chip", tier.tier().display(), tier.count());
    }

    private static float tierChipWidth(Ui ui, TierSummary tier) {
        return ui.textWidth("holy_chip", tierChipText(tier)) + ui.num(L + "holy_chip_pad_x") * 2 + ui.num("stroke.width") * 2;
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
        String tiers = tierLine(slots);
        if (!tiers.isEmpty()) {
            for (String line : ui.wrap("holy_line", tiers, textW)) {
                lines.add(new String[]{"holy_line", line, ""});
            }
        }
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
            HolyGear holy = view.holy();
            List<String> custom = holy.custom().stream().map(CustomEnchant.Found::text).toList();
            String enchants = switch (view.enchantStatus()) {
                case LISTED -> String.join(", ", concat(view.enchantments().stream().map(Component::getString)
                        .map(name -> holy.overCap().contains(name) ? Ui.tr("skirmish.gearinspector.holy.over_cap", name) : name)
                        .toList(), custom));
                case NO_DATA -> !custom.isEmpty() ? String.join(", ", custom)
                        : module.showMissingEnchantments.get() ? Ui.tr("skirmish.gearinspector.enchantments_no_data") : "";
                case GLINT_ONLY -> !custom.isEmpty() ? String.join(", ", custom)
                        : module.showMissingEnchantments.get() ? Ui.tr("skirmish.gearinspector.enchantments_glint_only") : "";
                case NOT_APPLICABLE -> String.join(", ", custom);
            };
            for (String line : enchants.isEmpty() ? List.<String>of() : ui.wrap("row", enchants, textW)) {
                lines.add(new String[]{"row", line, ""});
            }
            String talisman = talismanLine(holy.talisman());
            for (String line : talisman.isEmpty() ? List.<String>of() : ui.wrap("holy_line", talisman, textW)) {
                lines.add(new String[]{"holy_line", line, ""});
            }
            String hits = hitsLine(view);
            for (String line : hits.isEmpty() ? List.<String>of() : ui.wrap("holy_note", hits, textW)) {
                lines.add(new String[]{"holy_note", line, ""});
            }
        }
        return lines;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        if (b.isEmpty()) {
            return a;
        }
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    /** Dominant donor tier of the armour, from the slots read with the HolyWorld profile on; null otherwise. */
    private static @Nullable TierSummary armourTier(List<GearReader.SlotView> slots) {
        List<DonorTier> tiers = new ArrayList<>();
        for (GearReader.SlotView view : slots) {
            if (view.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                tiers.add(view.holy().tier());
            }
        }
        return TierSummary.of(tiers);
    }

    /** «Донат-броня: Infinity, 4/4 · в руке: Eternity», or "" when nothing HolyWorld-specific is worn. */
    private static String tierLine(List<GearReader.SlotView> slots) {
        List<String> parts = new ArrayList<>();
        TierSummary armour = armourTier(slots);
        if (armour != null) {
            parts.add(Ui.tr("skirmish.gearinspector.holy.tier_line", armour.tier().display(), armour.count()));
        }
        for (GearReader.SlotView view : slots) {
            if (view.slot() == EquipmentSlot.MAINHAND && view.holy().tier() != null) {
                parts.add(Ui.tr("skirmish.gearinspector.holy.hand_tier", view.holy().tier().display()));
            }
        }
        return String.join(" · ", parts);
    }

    /** «Урон II · Броня II · Скорость I · руна «Бессмертие»» for an off-hand sphere or talisman. */
    private static String talismanLine(@Nullable Talisman talisman) {
        if (talisman == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Talisman.Stat stat : talisman.stats()) {
            parts.add(Ui.tr("skirmish.gearinspector.holy.stat." + stat.type().name().toLowerCase(Locale.ROOT))
                    + " " + HolyText.roman(stat.level()));
        }
        if (talisman.rune() != null) {
            parts.add(Ui.tr("skirmish.gearinspector.holy.rune." + talisman.rune().name().toLowerCase(Locale.ROOT)));
        }
        return String.join(" · ", parts);
    }

    /** «≈ 1210 ударов до поломки (97% ударов без износа)» for armour, Lite's Unbreaking formula. */
    private String hitsLine(GearReader.SlotView view) {
        int unbreaking = view.holy().unbreaking();
        Durability durability = view.durability();
        if (unbreaking < 0 || !module.holyHitsLeft.get() || !durability.hasValue()) {
            return "";
        }
        long hits = LiteArmorWear.hitsLeft(durability.remaining(), unbreaking);
        if (hits < 0) {
            return "";
        }
        return Ui.tr("skirmish.gearinspector.holy.hits_left", hits, Ui.plural("skirmish.gearinspector.holy.hits", hits),
                LiteArmorWear.savePercent(unbreaking));
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

        // HolyWorld donor tier chip left of the ping pill; absent (and the layout unchanged) without a known tier.
        float headerRight = px;
        TierSummary tier = sample ? null : armourTier(slots(preview));
        if (tier != null) {
            String chip = tierChipText(tier);
            float cpx = ui.num(L + "holy_chip_pad_x");
            float cpy = ui.num(L + "holy_chip_pad_y");
            float chipW = tierChipWidth(ui, tier);
            float chipH = ui.lineHeight("holy_chip") + cpy * 2 + stroke * 2;
            float chipX = px - ui.num(L + "holy_chip_gap") - chipW;
            float chipY = cy + (face - chipH) / 2f;
            int tierColor = ui.color(tier.tier().group().colorToken());
            ui.box(chipX, chipY, chipW, chipH, chipH / 2f, ui.color("holy_chip_bg"), tierColor);
            ui.text("holy_chip", chip, chipX + stroke + cpx, chipY + stroke + cpy, tierColor);
            headerRight = chipX;
        }

        // Name and HP.
        float tx = cx + face + ui.num(L + "target_header_gap");
        float textW = headerRight - ui.num(L + "target_header_gap") - tx;
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
