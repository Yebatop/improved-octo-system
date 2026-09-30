package dev.skirmish.module.navigator;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.util.ServerContext;
import dev.skirmish.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Route card under the waypoint pill, shown when the way goes through portals or the target is in another
 * dimension: the target and ETA, then the steps (current one with a direction arrow). Without a known portal it
 * says where the target is in this dimension's coordinates.
 */
final class RouteCard extends HudBlock {
    private static final String L = NavigatorModule.L;
    private final NavigatorModule module;

    private record Row(String text, String dist, String tone, boolean current, float angle) {
    }

    private String title = "";
    private String eta = "";
    private List<Row> rows = List.of();

    RouteCard(NavigatorModule module) {
        super("route", "skirmish.hud.element.route", new Placement(0.5f, 0, 0.5f, 0, 0, Theme.get().num("layout.hud.top_column_dy")));
        this.module = module;
    }

    @Override
    public @Nullable String stackUnder() {
        return "waypoint";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.routeCard.get();
    }

    @Override
    public boolean shown() {
        update(false);
        return !rows.isEmpty();
    }

    @Override
    public void update(boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Waypoint target = module.routeTarget();
        List<Row> out = new ArrayList<>();
        if (player != null && target != null) {
            String dim = ServerContext.dimension();
            RoutePlanner.Route route = module.route();
            boolean otherDim = !target.dimension().equals(dim);
            title = Ui.tr("skirmish.navigator.route_to", target.name());
            if (route != null && (route.viaPortals() || otherDim)) {
                long eta = module.etaSeconds();
                this.eta = eta < 0 ? "" : Ui.duration(eta * 1000);
                boolean first = true;
                for (RoutePlanner.Leg leg : route.legs()) {
                    String text = leg.portal()
                            ? Ui.tr(RoutePlanner.OVERWORLD.equals(leg.dim()) ? "skirmish.navigator.to_portal_nether" : "skirmish.navigator.to_portal_world")
                            : Ui.tr("skirmish.navigator.to_target");
                    String tone = RoutePlanner.NETHER.equals(leg.dim()) ? "nav_nether" : "nav_world";
                    float angle = 0f;
                    if (first && leg.dim().equals(dim)) {
                        float yaw = (float) Math.toDegrees(Math.atan2(-(leg.tx() - player.getX()), leg.tz() - player.getZ()));
                        angle = Mth.wrapDegrees(yaw - player.getViewYRot(1f));
                    }
                    double len = first && leg.dim().equals(dim) ? Math.hypot(leg.tx() - player.getX(), leg.tz() - player.getZ()) : leg.length();
                    out.add(new Row(text, distance(len), tone, first, angle));
                    first = false;
                    if (out.size() >= 3) {
                        break;
                    }
                }
            } else if (route == null && otherDim) {
                this.eta = "";
                double tx = RoutePlanner.convert(target.x(), target.dimension(), dim);
                double tz = RoutePlanner.convert(target.z(), target.dimension(), dim);
                out.add(new Row(Ui.tr("skirmish.navigator.no_portal", (long) Math.floor(tx), (long) Math.floor(tz)), "", "warn", true, 0f));
            }
        } else if (preview) {
            title = Ui.tr("skirmish.navigator.route_to", "База");
            eta = "2:10";
            out.add(new Row(Ui.tr("skirmish.navigator.to_portal_nether"), "120 м", "nav_world", true, 25f));
            out.add(new Row(Ui.tr("skirmish.navigator.to_portal_world"), "430 м", "nav_nether", false, 0f));
            out.add(new Row(Ui.tr("skirmish.navigator.to_target"), "60 м", "nav_world", false, 0f));
        }
        rows = out;
    }

    private static String distance(double blocks) {
        return blocks >= 1000 ? Ui.decimal(blocks / 1000.0, 1) + " " + Ui.tr("skirmish.elytra.km")
                : Math.round(blocks) + " " + Ui.tr("skirmish.elytra.m");
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "card_width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return ui.num(L + "card_pad") * 2 + ui.lineHeight("nav_title") + ui.num(L + "card_gap")
                + rows.size() * (ui.lineHeight("nav_row") + ui.num(L + "row_gap"));
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float h = height(ui, preview);
        float pad = ui.num(L + "card_pad");
        ui.box(x, y, w, h, ui.theme().radius("tile"), ui.color("panel"), ui.color("stroke"));
        float ew = ui.textWidth("nav_eta", eta);
        ui.text("nav_eta", eta, x + w - pad - ew, y + pad, ui.color("accent"));
        ui.text("nav_title", ui.ellipsize("nav_title", title, w - pad * 3 - ew), x + pad, y + pad);
        float cy = y + pad + ui.lineHeight("nav_title") + ui.num(L + "card_gap");
        float icon = ui.num(L + "row_icon");
        for (Row row : rows) {
            float rh = ui.lineHeight("nav_row");
            if (row.current() && row.angle() != 0f) {
                Icons.navArrow(ui, x + pad, cy + (rh - icon) / 2f, icon, 2f, ui.color("accent"), row.angle());
            } else {
                ui.circle(x + pad + icon / 2f, cy + rh / 2f, icon * 0.45f, ui.color(row.tone()));
            }
            float dw = ui.textWidth("nav_dist", row.dist());
            ui.text("nav_dist", row.dist(), x + w - pad - dw, cy, ui.color(row.current() ? "text" : "text_3"));
            float tx = x + pad + icon + ui.num(L + "row_icon_gap");
            ui.text("nav_row", ui.ellipsize("nav_row", row.text(), w - pad * 2 - icon - dw - 16), tx, cy,
                    ui.color(row.current() ? "text" : "text_2"));
            cy += rh + ui.num(L + "row_gap");
        }
    }
}
