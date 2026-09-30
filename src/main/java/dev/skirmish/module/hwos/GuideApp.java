package dev.skirmish.module.hwos;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.ModuleManager;
import dev.skirmish.module.events.EventSchedule;
import dev.skirmish.module.events.EventsModule;
import dev.skirmish.module.events.Rarity;
import dev.skirmish.module.regions.RegionTable;
import dev.skirmish.ui.Ui;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Guide app: region sizes of Lite and Prime, the fixed schedule with your local times, event rarities, the mod rules
 * of HolyWorld (item 2.4) and every Skirmish key with what it is bound to now.
 */
final class GuideApp implements HwOsScreen.OsApp {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String id() {
        return "guide";
    }

    @Override
    public void draw(HwOsScreen s, Ui ui, float x, float y, float w, float h, double mx, double my) {
        String L = HwOsScreen.L;
        float gap = ui.num(L + "col_gap");
        float colW = (w - gap) / 2f;
        float lh = ui.lineHeight("hwos_row") + 5;
        s.clip(ui, x - 4, y, x + w + 4, y + h);
        float top = y - s.scroll();

        // Left: regions, schedule, rarities.
        float ly = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.guide.regions"), x, top);
        for (RegionTable.Server server : RegionTable.Server.values()) {
            ui.text("menu_row_desc", Ui.tr("skirmish.hwos.guide.server." + server.name().toLowerCase(java.util.Locale.ROOT)), x, ly, ui.color("accent"));
            ly += ui.lineHeight("menu_row_desc") + 3;
            for (Map.Entry<RegionTable.Type, String> e : kinds(server).entrySet()) {
                String size = RegionTable.sizeText(e.getKey());
                float vw = ui.textWidth("hwos_value", size);
                ui.text("hwos_value", size, x + colW - vw, ly);
                ui.text("hwos_row", ui.ellipsize("hwos_row", e.getValue(), colW - vw - 10), x, ly);
                ly += lh;
            }
            ly += 6;
        }
        ly = HwOsScreen.para(ui, "menu_hint", Ui.tr("skirmish.hwos.guide.regions_note"), x, ly, colW, ui.color("text_4")) + gap / 2;

        ly = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.guide.schedule"), x, ly);
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        EventSchedule.Window end = EventSchedule.endCapture(now);
        boolean endOn = !now.isBefore(end.start());
        DateTimeFormatter when = DateTimeFormatter.ofPattern("EEE HH:mm", gameLocale());
        String endValue = endOn ? Ui.tr("skirmish.hwos.guide.until", CLOCK.format(end.end().atZone(zone))) : when.format(end.start().atZone(zone));
        ly = scheduleRow(ui, x, ly, colW, Ui.tr("skirmish.commander.kind.end"), Ui.tr("skirmish.hwos.guide.end_rule"), endValue, endOn ? "good" : "text");
        ly = scheduleRow(ui, x, ly, colW, Ui.tr("skirmish.commander.kind.bunker"), Ui.tr("skirmish.hwos.guide.bunker_rule"),
                CLOCK.format(EventSchedule.nextBunker(now).atZone(zone)), "text");
        EventsModule ev = ModuleManager.get().byId(EventsModule.ID) instanceof EventsModule e && e.isEnabled() ? e : null;
        Instant vote = ev == null ? null : ev.nextVote(now);
        ly = scheduleRow(ui, x, ly, colW, Ui.tr("skirmish.commander.kind.vote"), Ui.tr("skirmish.hwos.guide.vote_rule"),
                vote == null ? "—" : "≈" + CLOCK.format(vote.atZone(zone)), "text");
        ly = scheduleRow(ui, x, ly, colW, Ui.tr("skirmish.commander.kind.restart"), Ui.tr("skirmish.hwos.guide.restart_rule"),
                CLOCK.format(EventSchedule.nextRestart(now).atZone(zone)), "text");
        ly = HwOsScreen.para(ui, "menu_hint", Ui.tr("skirmish.hwos.guide.local_time", zone.getId()), x, ly, colW, ui.color("text_4")) + gap / 2;

        ly = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.guide.rarity"), x, ly);
        for (Rarity r : new Rarity[]{Rarity.LEGENDARY, Rarity.EPIC, Rarity.RARE, Rarity.COMMON}) {
            ui.circle(x + 4, ly + ui.lineHeight("hwos_row") / 2f, 8, ui.color(HwOsScreen.rarityTone(r)));
            ui.text("hwos_row", Ui.tr("skirmish.events.rarity." + r.key()), x + 14, ly, ui.color(HwOsScreen.rarityTone(r)));
            ly += lh;
        }

        // Right: mod rules, keys.
        float rx = x + colW + gap;
        float ry = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.guide.rules"), rx, top);
        ui.text("menu_row_desc", Ui.tr("skirmish.hwos.guide.forbidden"), rx, ry, ui.color("bad"));
        ry += ui.lineHeight("menu_row_desc") + 3;
        for (String key : new String[]{"xray", "esp", "trajectories", "elytra_swap", "scripts", "automation", "invisible"}) {
            ry = bullet(ui, rx, ry, colW, Ui.tr("skirmish.hwos.guide.rule." + key), "bad");
        }
        ry += 4;
        ui.text("menu_row_desc", Ui.tr("skirmish.hwos.guide.allowed"), rx, ry, ui.color("good"));
        ry += ui.lineHeight("menu_row_desc") + 3;
        for (String key : new String[]{"particles", "hp", "invisible_block", "scroll"}) {
            ry = bullet(ui, rx, ry, colW, Ui.tr("skirmish.hwos.guide.rule." + key), "good");
        }
        ry = HwOsScreen.para(ui, "menu_hint", Ui.tr("skirmish.hwos.guide.rules_note"), rx, ry + 4, colW, ui.color("text_4")) + gap / 2;

        ry = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.guide.keys"), rx, ry);
        List<KeyMapping> keys = new ArrayList<>();
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
            if (k.getCategory() == SkirmishKeys.CATEGORY) {
                keys.add(k);
            }
        }
        for (KeyMapping k : keys) {
            String bound = k.isUnbound() ? Ui.tr("skirmish.hwos.guide.unbound") : k.getTranslatedKeyMessage().getString();
            float vw = ui.textWidth("hwos_value", bound);
            ui.text("hwos_value", bound, rx + colW - vw, ry, ui.color(k.isUnbound() ? "text_4" : "accent"));
            ui.text("hwos_row", ui.ellipsize("hwos_row", Component.translatable(k.getName()).getString(), colW - vw - 10), rx, ry);
            ry += lh;
        }
        s.unclip(ui);
        s.content(Math.max(ly, ry) - top, h);
    }

    /** One line per region kind: the block names that make it (by size). */
    private static Map<RegionTable.Type, String> kinds(RegionTable.Server server) {
        Map<RegionTable.Type, List<String>> blocks = new LinkedHashMap<>();
        for (Map.Entry<String, RegionTable.Type> e : RegionTable.table(server).entrySet()) {
            blocks.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }
        List<RegionTable.Type> order = new ArrayList<>(blocks.keySet());
        order.sort(Comparator.comparingInt(RegionTable.Type::size).thenComparing(RegionTable.Type::id));
        Map<RegionTable.Type, String> out = new LinkedHashMap<>();
        for (RegionTable.Type t : order) {
            List<String> ids = blocks.get(t);
            ids.sort(Comparator.comparingInt(String::length));
            String name = blockName(ids.getFirst());
            out.put(t, ids.size() > 1 ? Ui.tr("skirmish.hwos.guide.any", name) : name);
        }
        return out;
    }

    /** The game's language as a Java locale ("ru_ru" → ru), for weekday names. */
    private static java.util.Locale gameLocale() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return java.util.Locale.forLanguageTag(code.replace('_', '-'));
    }

    private static String blockName(String id) {
        try {
            return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)).getName().getString();
        } catch (RuntimeException e) {
            return id;
        }
    }

    private static float scheduleRow(Ui ui, float x, float y, float w, String title, String rule, String value, String tone) {
        EventsApp.row(ui, x, y, w, title, rule, value, tone);
        return y + ui.lineHeight("hwos_row") + 1 + ui.lineHeight("menu_row_desc") + 6;
    }

    private static float bullet(Ui ui, float x, float y, float w, String text, String tone) {
        float lh = ui.lineHeight("menu_row_desc");
        ui.circle(x + 3, y + lh / 2f, 5, ui.color(tone));
        for (String line : ui.wrap("menu_row_desc", text, w - 12)) {
            ui.text("menu_row_desc", line, x + 12, y, ui.color("text_2"));
            y += lh;
        }
        return y + 2;
    }
}
