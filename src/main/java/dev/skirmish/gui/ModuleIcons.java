package dev.skirmish.gui;

import dev.skirmish.module.Module;
import dev.skirmish.ui.Ui;

/**
 * The Skirmish logo and a line icon per module (24-unit view box, round joins, like {@link dev.skirmish.ui.Icons});
 * modules without their own icon use their category's.
 */
public final class ModuleIcons {
    private static final float STROKE = 2f;

    private ModuleIcons() {
    }

    /** Logo emblem: an angular «S» cut in two by a slash; drawn across the whole {@code size} box. */
    public static void logo(Ui ui, float x, float y, float size, int color, int slash) {
        float s = size / 24f;
        float w = 2.4f;
        path(ui, x, y, s, w, color, 17f, 4.825f, 8f, 4.825f, 5.5f, 7.325f, 5.5f, 9.825f, 10.6f, 11.525f);
        path(ui, x, y, s, w, color, 13.4f, 12.475f, 18.5f, 14.175f, 18.5f, 16.675f, 16f, 19.175f, 7f, 19.175f);
        path(ui, x, y, s, 0.9f, slash, 4.2f, 19.8f, 8.3f, 15.7f);
        path(ui, x, y, s, 0.9f, slash, 15.7f, 8.3f, 19.8f, 4.2f);
    }

    /** The module's icon, or its category's when it has none. */
    static void draw(Ui ui, Module module, float x, float y, float size, int color) {
        if (!drawOwn(ui, module.id(), x, y, size, color)) {
            CategoryIcons.draw(ui, module.category(), x, y, size, color);
        }
    }

    private static boolean drawOwn(Ui ui, String id, float x, float y, float size, int c) {
        float s = size / 24f;
        switch (id) {
            case "combat" -> // pulse line
                    p(ui, x, y, s, c, 2f, 12f, 6f, 12f, 9f, 5f, 15f, 19f, 18f, 12f, 22f, 12f);
            case "killcam" -> { // video camera
                ui.border(x + 2f * s, y + 6f * s, 14f * s, 12f * s, 2.5f * s, STROKE * s, c);
                p(ui, x, y, s, c, 16f, 10.5f, 22f, 7f, 22f, 17f, 16f, 13.5f);
            }
            case "killcard" -> { // picture card
                ui.border(x + 3f * s, y + 3f * s, 18f * s, 18f * s, 2.5f * s, STROKE * s, c);
                ui.ring(x + 9f * s, y + 9f * s, 4f * s + STROKE * s, STROKE * s, c);
                p(ui, x, y, s, c, 21f, 15f, 16f, 10f, 5f, 21f);
            }
            case "gearinspector" -> { // shield with a check
                p(ui, x, y, s, c, 12f, 2.5f, 20f, 5.5f, 20f, 12f, 16.5f, 18f, 12f, 21.5f, 7.5f, 18f, 4f, 12f, 4f, 5.5f, 12f, 2.5f);
                p(ui, x, y, s, c, 8.5f, 12f, 11f, 14.5f, 15.5f, 9.5f);
            }
            case "pvp" -> { // crossed swords
                p(ui, x, y, s, c, 14.5f, 17.5f, 3f, 6f, 3f, 3f, 6f, 3f, 17.5f, 14.5f);
                p(ui, x, y, s, c, 13f, 19f, 19f, 13f);
                p(ui, x, y, s, c, 16f, 16f, 20f, 20f);
                p(ui, x, y, s, c, 19f, 21f, 21f, 19f);
                p(ui, x, y, s, c, 14.5f, 6.5f, 18f, 3f, 21f, 3f, 21f, 6f, 17.5f, 9.5f);
                p(ui, x, y, s, c, 5f, 14f, 9f, 18f);
                p(ui, x, y, s, c, 7f, 17f, 4f, 20f);
                p(ui, x, y, s, c, 3f, 19f, 5f, 21f);
            }
            case "effect_hud" -> { // flask
                p(ui, x, y, s, c, 10f, 2.5f, 10f, 9.5f, 4.5f, 20f, 5.5f, 21.5f, 18.5f, 21.5f, 19.5f, 20f, 14f, 9.5f, 14f, 2.5f);
                p(ui, x, y, s, c, 8.5f, 2.5f, 15.5f, 2.5f);
                p(ui, x, y, s, c, 7f, 16f, 17f, 16f);
            }
            case "armor_hud" -> // chestplate
                    p(ui, x, y, s, c, 8f, 3f, 4f, 5f, 2.5f, 10f, 5.5f, 11f, 6f, 21f, 18f, 21f, 18.5f, 11f, 21.5f, 10f, 20f, 5f,
                            16f, 3f, 14.5f, 5.5f, 12f, 6.5f, 9.5f, 5.5f, 8f, 3f);
            case "clanshare" -> { // share: three nodes
                ui.ring(x + 18f * s, y + 5f * s, 6f * s + STROKE * s, STROKE * s, c);
                ui.ring(x + 6f * s, y + 12f * s, 6f * s + STROKE * s, STROKE * s, c);
                ui.ring(x + 18f * s, y + 19f * s, 6f * s + STROKE * s, STROKE * s, c);
                p(ui, x, y, s, c, 8.6f, 13.5f, 15.4f, 17.5f);
                p(ui, x, y, s, c, 15.4f, 6.5f, 8.6f, 10.5f);
            }
            case "fullbright" -> { // sun
                ui.ring(x + 12f * s, y + 12f * s, 8f * s + STROKE * s, STROKE * s, c);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float cos = (float) Math.cos(a);
                    float sin = (float) Math.sin(a);
                    p(ui, x, y, s, c, 12f + 7f * cos, 12f + 7f * sin, 12f + 10f * cos, 12f + 10f * sin);
                }
            }
            case "anvilcalc" -> { // anvil
                p(ui, x, y, s, c, 2f, 6f, 17f, 6f, 22f, 7.5f, 19f, 10f, 15.5f, 10f, 14.5f, 14f, 17f, 16f, 17f, 19f, 7f, 19f, 7f, 16f,
                        9.5f, 14f, 8.5f, 10f, 5f, 10f, 2f, 8f, 2f, 6f);
            }
            case "coords_hud" -> { // locate
                ui.ring(x + 12f * s, y + 12f * s, 14f * s + STROKE * s, STROKE * s, c);
                ui.circle(x + 12f * s, y + 12f * s, 4f * s, c);
                p(ui, x, y, s, c, 12f, 1.5f, 12f, 5f);
                p(ui, x, y, s, c, 12f, 19f, 12f, 22.5f);
                p(ui, x, y, s, c, 1.5f, 12f, 5f, 12f);
                p(ui, x, y, s, c, 19f, 12f, 22.5f, 12f);
            }
            case "death_waypoint" -> { // gravestone with a cross
                float[] stone = new float[2 * 11 + 4];
                stone[0] = 6f;
                stone[1] = 21f;
                for (int i = 0; i <= 10; i++) {
                    double a = Math.PI - Math.PI * i / 10;
                    stone[2 + i * 2] = 12f + 6f * (float) Math.cos(a);
                    stone[3 + i * 2] = 9f - 6f * (float) Math.sin(a);
                }
                stone[stone.length - 2] = 18f;
                stone[stone.length - 1] = 21f;
                p(ui, x, y, s, c, stone);
                p(ui, x, y, s, c, 3.5f, 21f, 20.5f, 21f);
                p(ui, x, y, s, c, 12f, 9f, 12f, 17f);
                p(ui, x, y, s, c, 9f, 12f, 15f, 12f);
            }
            case "events" -> // bolt
                    p(ui, x, y, s, c, 13f, 2f, 4f, 14f, 11f, 14f, 10f, 22f, 19f, 10f, 12f, 10f, 13f, 2f);
            case "alerts" -> { // warning triangle
                p(ui, x, y, s, c, 12f, 3f, 22f, 20.5f, 2f, 20.5f, 12f, 3f);
                p(ui, x, y, s, c, 12f, 9.5f, 12f, 14f);
                ui.circle(x + 12f * s, y + 17.3f * s, 2.4f * s, c);
            }
            case "low_fire" -> // flame
                    p(ui, x, y, s, c, 12f, 2.5f, 15.5f, 7f, 18.5f, 11.5f, 18.5f, 15.5f, 16.5f, 19.5f, 12f, 21.5f, 7.5f, 19.5f,
                            5.5f, 15.5f, 6f, 11.5f, 8.5f, 8.5f, 10f, 12f, 12f, 2.5f);
            case "camera_comfort" -> { // camera
                p(ui, x, y, s, c, 2f, 8f, 2f, 20f, 22f, 20f, 22f, 8f, 16.5f, 8f, 15f, 5f, 9f, 5f, 7.5f, 8f, 2f, 8f);
                ui.ring(x + 12f * s, y + 13.5f * s, 8f * s + STROKE * s, STROKE * s, c);
            }
            case "zoom" -> { // magnifier with plus
                ui.ring(x + 11f * s, y + 11f * s, 14f * s + STROKE * s, STROKE * s, c);
                p(ui, x, y, s, c, 16.5f, 16.5f, 21f, 21f);
                p(ui, x, y, s, c, 8f, 11f, 14f, 11f);
                p(ui, x, y, s, c, 11f, 8f, 11f, 14f);
            }
            case "market" -> { // shopping bag
                p(ui, x, y, s, c, 6f, 2.5f, 3f, 6.5f, 3f, 21.5f, 21f, 21.5f, 21f, 6.5f, 18f, 2.5f, 6f, 2.5f);
                p(ui, x, y, s, c, 3f, 6.5f, 21f, 6.5f);
                float[] handle = new float[2 * 9];
                for (int i = 0; i <= 8; i++) {
                    double a = Math.PI * i / 8;
                    handle[i * 2] = 12f + 4f * (float) Math.cos(a);
                    handle[i * 2 + 1] = 10f + 4f * (float) Math.sin(a);
                }
                p(ui, x, y, s, c, handle);
            }
            case "fight_review" -> { // bar chart
                p(ui, x, y, s, c, 3f, 3f, 3f, 21f, 21f, 21f);
                p(ui, x, y, s, c, 8f, 17f, 8f, 12f);
                p(ui, x, y, s, c, 13f, 17f, 13f, 7f);
                p(ui, x, y, s, c, 18f, 17f, 18f, 10f);
            }
            case "combo_hud" -> { // double chevron up
                p(ui, x, y, s, c, 5f, 12f, 12f, 5f, 19f, 12f);
                p(ui, x, y, s, c, 5f, 19f, 12f, 12f, 19f, 19f);
            }
            case "kill_feed" -> { // list
                for (float ly : new float[]{6f, 12f, 18f}) {
                    ui.circle(x + 4f * s, y + ly * s, 3f * s, c);
                    p(ui, x, y, s, c, 9f, ly, 21f, ly);
                }
            }
            case "dossier" -> { // id card
                ui.border(x + 2f * s, y + 5f * s, 20f * s, 14f * s, 2.5f * s, STROKE * s, c);
                ui.ring(x + 8f * s, y + 10.5f * s, 5f * s + STROKE * s, STROKE * s, c);
                p(ui, x, y, s, c, 5.5f, 15.5f, 10.5f, 15.5f);
                p(ui, x, y, s, c, 14f, 10f, 19f, 10f);
                p(ui, x, y, s, c, 14f, 14f, 17.5f, 14f);
            }
            case "nametag_hp" -> { // heart
                int n = 28;
                float[] heart = new float[(n + 1) * 2];
                for (int i = 0; i <= n; i++) {
                    double t = Math.PI * 2 * i / n;
                    double sin = Math.sin(t);
                    heart[i * 2] = 12f + 0.55f * (float) (16 * sin * sin * sin);
                    heart[i * 2 + 1] = 11.8f - 0.55f * (float) (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t));
                }
                p(ui, x, y, s, c, heart);
            }
            case "friends" -> { // two people
                ui.ring(x + 9f * s, y + 7.5f * s, 7f * s + STROKE * s, STROKE * s, c);
                p(ui, x, y, s, c, arc(9f, 21f, 6.5f, 180, 0));
                ui.ring(x + 17f * s, y + 8.5f * s, 5f * s + STROKE * s, STROKE * s, c);
                p(ui, x, y, s, c, arc(17f, 21f, 5f, 75, 0));
            }
            case "survival_alerts" -> { // bell
                float[] top = arc(12f, 10f, 6f, 180, 0);
                float[] bell = new float[top.length + 8];
                bell[0] = 4.5f;
                bell[1] = 17f;
                bell[2] = 6f;
                bell[3] = 15f;
                System.arraycopy(top, 0, bell, 4, top.length);
                bell[bell.length - 4] = 18f;
                bell[bell.length - 3] = 15f;
                bell[bell.length - 2] = 19.5f;
                bell[bell.length - 1] = 17f;
                p(ui, x, y, s, c, bell);
                p(ui, x, y, s, c, 4.5f, 17f, 19.5f, 17f);
                p(ui, x, y, s, c, 10f, 20.5f, 14f, 20.5f);
            }
            case "item_counter" -> { // box
                p(ui, x, y, s, c, 12f, 2.5f, 21f, 7f, 21f, 17f, 12f, 21.5f, 3f, 17f, 3f, 7f, 12f, 2.5f);
                p(ui, x, y, s, c, 3f, 7f, 12f, 11.5f, 21f, 7f);
                p(ui, x, y, s, c, 12f, 11.5f, 12f, 21.5f);
            }
            case "lag_meter" -> { // signal bars
                p(ui, x, y, s, c, 5f, 20f, 5f, 16f);
                p(ui, x, y, s, c, 10f, 20f, 10f, 12f);
                p(ui, x, y, s, c, 15f, 20f, 15f, 8f);
                p(ui, x, y, s, c, 20f, 20f, 20f, 4f);
            }
            case "talisman_badges" -> { // price tag
                p(ui, x, y, s, c, 3f, 3f, 11.5f, 3f, 21f, 12.5f, 12.5f, 21f, 3f, 11.5f, 3f, 3f);
                ui.circle(x + 7.5f * s, y + 7.5f * s, 3.2f * s, c);
            }
            case "hw_tooltips" -> { // info bubble
                ui.border(x + 2.5f * s, y + 3f * s, 19f * s, 14f * s, 3f * s, STROKE * s, c);
                p(ui, x, y, s, c, 8f, 17f, 7f, 21.5f, 12.5f, 17f);
                ui.circle(x + 12f * s, y + 6.8f * s, 2.4f * s, c);
                p(ui, x, y, s, c, 12f, 10f, 12f, 14f);
            }
            case "session_recap" -> { // trophy
                p(ui, x, y, s, c, 7f, 3f, 17f, 3f, 17f, 9f, 16f, 12f, 14f, 13.5f, 12f, 14f, 10f, 13.5f, 8f, 12f, 7f, 9f, 7f, 3f);
                p(ui, x, y, s, c, 7f, 5f, 3.5f, 5f, 3.5f, 7f, 5f, 9.5f, 7.3f, 10.2f);
                p(ui, x, y, s, c, 17f, 5f, 20.5f, 5f, 20.5f, 7f, 19f, 9.5f, 16.7f, 10.2f);
                p(ui, x, y, s, c, 12f, 14f, 12f, 18f);
                p(ui, x, y, s, c, 8f, 21f, 16f, 21f, 15f, 18f, 9f, 18f, 8f, 21f);
            }
            case "waypoints" -> CategoryIcons.pin(ui, x, y, size, c);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Arc points (view box units) around (cx, cy) from {@code from} to {@code to} degrees, y up. */
    private static float[] arc(float cx, float cy, float r, int from, int to) {
        int n = Math.max(2, Math.abs(to - from) / 15);
        float[] pts = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(from + (to - from) * (double) i / n);
            pts[i * 2] = cx + r * (float) Math.cos(a);
            pts[i * 2 + 1] = cy - r * (float) Math.sin(a);
        }
        return pts;
    }

    private static void p(Ui ui, float x, float y, float s, int color, float... points) {
        path(ui, x, y, s, STROKE, color, points);
    }

    private static void path(Ui ui, float x, float y, float s, float stroke, int color, float... points) {
        float[] scaled = new float[points.length];
        for (int i = 0; i < points.length; i += 2) {
            scaled[i] = x + points[i] * s;
            scaled[i + 1] = y + points[i + 1] * s;
        }
        ui.polyline(stroke * s, color, scaled);
    }
}
