package dev.skirmish.module.elytra;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.util.ServerContext;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The flight panel: title with the speed, then rows of label and value. Default place: the left edge a little below
 * the middle, away from the crosshair and the hotbar.
 */
final class ElytraHud extends HudBlock {
    private static final String L = ElytraHudModule.L;
    private final ElytraHudModule module;

    private record Row(String label, String value, String tone) {
    }

    private String speed = "";
    private List<Row> rows = List.of();

    ElytraHud(ElytraHudModule module) {
        super(ElytraHudModule.ID, "skirmish.hud.element.elytra_hud", new Placement(0, 0.5f, 0, 0.5f,
                Theme.get().num(L + "default_x"), Theme.get().num(L + "default_y")));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public boolean shown() {
        update(false);
        return module.flying();
    }

    @Override
    public boolean hasContent() {
        return !rows.isEmpty();
    }

    @Override
    public void update(boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !module.flying()) {
            if (preview && rows.isEmpty()) {
                speed = Ui.tr("skirmish.elytra.speed", Ui.decimal(34.6, 1));
                rows = List.of(new Row(Ui.tr("skirmish.elytra.height"), "142 · −8°", "text"),
                        new Row(Ui.tr("skirmish.elytra.rockets"), "23", "text"),
                        new Row(Ui.tr("skirmish.elytra.elytra"), "7:12", "text"),
                        new Row("«База»", "1,2 км · 0:35", "accent"));
            }
            return;
        }
        speed = Ui.tr("skirmish.elytra.speed", Ui.decimal(module.speed(), 1));
        List<Row> out = new ArrayList<>();
        String height = Integer.toString((int) Math.floor(player.getY()));
        if (module.angle.get()) {
            int pitch = Math.round(-player.getXRot());
            height += " · " + (pitch > 0 ? "+" : pitch < 0 ? "−" : "") + Math.abs(pitch) + "°";
        }
        out.add(new Row(Ui.tr("skirmish.elytra.height"), height, "text"));
        int rockets = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.FIREWORK_ROCKET)) {
                rockets += stack.getCount();
            }
        }
        out.add(new Row(Ui.tr("skirmish.elytra.rockets"), Integer.toString(rockets), rockets == 0 ? "bad" : rockets < 8 ? "warn" : "text"));
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.has(DataComponents.GLIDER) && chest.isDamageableItem()) {
            int seconds = FlightMath.glideSeconds(chest.getMaxDamage() - chest.getDamageValue());
            out.add(new Row(Ui.tr("skirmish.elytra.elytra"), Ui.duration(seconds * 1000L), seconds < 60 ? "bad" : seconds < 180 ? "warn" : "text"));
        }
        if (module.waypoint.get()) {
            Waypoint target = WaypointManager.get().selected();
            if (target != null && target.dimension().equals(ServerContext.dimension())) {
                double dx = target.x() - player.getX();
                double dz = target.z() - player.getZ();
                double distance = Math.sqrt(dx * dx + dz * dz);
                char sep = Ui.decimal(1.5, 1).charAt(1);
                String value = FlightMath.distanceText(distance, sep, Ui.tr("skirmish.elytra.m"), Ui.tr("skirmish.elytra.km"));
                long eta = FlightMath.etaSeconds(distance, module.horizontalSpeed());
                if (eta >= 0) {
                    value += " · " + Ui.duration(eta * 1000L);
                }
                out.add(new Row("«" + target.name() + "»", value, "accent"));
            }
        }
        rows = out;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return ui.num(L + "pad_y") * 2 + ui.lineHeight("el_speed") + ui.num(L + "title_gap")
                + rows.size() * (ui.lineHeight("el_row") + ui.num(L + "row_gap"));
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float h = height(ui, preview);
        ui.box(x, y, w, h, ui.theme().radius("tile"), ui.color("panel"), ui.color("stroke"));
        float padX = ui.num(L + "pad_x");
        float cy = y + ui.num(L + "pad_y");
        ui.text("el_title", Ui.tr("skirmish.elytra.title"), x + padX, cy + (ui.lineHeight("el_speed") - ui.lineHeight("el_title")) / 2f);
        float sw = ui.textWidth("el_speed", speed);
        ui.text("el_speed", speed, x + w - padX - sw, cy, ui.color("accent"));
        cy += ui.lineHeight("el_speed") + ui.num(L + "title_gap");
        for (Row row : rows) {
            ui.text("el_label", ui.ellipsize("el_label", row.label(), w * 0.45f), x + padX, cy);
            float vw = ui.textWidth("el_row", row.value());
            ui.text("el_row", row.value(), x + w - padX - vw, cy, ui.color(row.tone()));
            cy += ui.lineHeight("el_row") + ui.num(L + "row_gap");
        }
    }
}
