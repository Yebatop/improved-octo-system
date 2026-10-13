package dev.skirmish.module.analytics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.analytics.combo.ComboHudModule;
import dev.skirmish.module.analytics.dossier.DossierModule;
import dev.skirmish.module.analytics.feed.KillFeedModule;
import dev.skirmish.module.analytics.review.FightReviewModule;
import dev.skirmish.module.analytics.review.Outcome;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.setting.Setting;
import dev.skirmish.ui.Theme;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Feature ids, categories, every visible string in both languages, theme tokens. */
class AnalyticsModulesTest {
    private static final String NS = "skirmish_analytics";

    private static List<Module> modules() {
        return List.of(new FightReviewModule(), new ComboHudModule(), new KillFeedModule(), new DossierModule());
    }

    private static JsonObject lang(String code) throws IOException {
        try (InputStream in = AnalyticsModulesTest.class.getResourceAsStream("/assets/" + NS + "/lang/" + code + ".json")) {
            assertNotNull(in, code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void featureIdsAndCategories() {
        List<String> ids = List.of("fight_review", "combo_hud", "kill_feed", "dossier");
        List<Module> modules = modules();
        for (int i = 0; i < ids.size(); i++) {
            Module m = modules.get(i);
            assertEquals(ids.get(i), m.id());
            assertEquals(ids.get(i), m.featureId());
            assertEquals(Category.COMBAT, m.category());
            assertTrue(m.canToggle());
        }
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        assertEquals(lang("en_us").keySet(), lang("ru_ru").keySet());
    }

    @Test
    void everySettingIsTranslated() throws IOException {
        Set<String> keys = lang("ru_ru").keySet();
        for (Module m : modules()) {
            assertTrue(keys.contains(m.nameKey()), m.nameKey());
            assertTrue(keys.contains(m.descriptionKey()), m.descriptionKey());
            for (Setting<?> s : m.settings()) {
                assertTrue(keys.contains(s.translationKey()), s.translationKey());
                assertTrue(keys.contains(s.translationKey() + ".tooltip"), s.translationKey());
                if (s instanceof KeySetting key) {
                    assertTrue(keys.contains(key.mappingName()), key.mappingName());
                }
            }
        }
    }

    @Test
    void dynamicKeysAreTranslated() throws IOException {
        Set<String> keys = lang("ru_ru").keySet();
        for (Outcome o : Outcome.values()) {
            assertTrue(keys.contains(o.langKey()), o.langKey());
        }
        for (TimeAgo.Unit u : TimeAgo.Unit.values()) {
            String key = "skirmish.analytics.ago." + u.name().toLowerCase(Locale.ROOT);
            assertTrue(keys.contains(key), key);
        }
        for (String type : List.of("player_attack", "mob_attack", "generic", "arrow", "trident", "wind_charge", "fireworks", "fall",
                "lava", "on_fire", "explosion", "magic", "thorns", "wither", "drown", "starve", "out_of_world", "in_wall", "cactus",
                "freeze", "lightning_bolt", "falling_anvil")) {
            String key = "skirmish.analytics.damage." + DamageTypes.label("minecraft:" + type);
            assertTrue(keys.contains(key), key);
        }
        for (String form : List.of("one", "few", "many")) {
            assertTrue(keys.contains("skirmish.analytics.dossier.fights." + form));
        }
        for (String el : List.of("fight_review_hint", "combo", "kill_feed", "dossier")) {
            assertTrue(keys.contains("skirmish.hud.element." + el), el);
        }
    }

    /** Every literal "skirmish.analytics.…" key in the sources exists (prefixes ending with a dot are built at runtime). */
    @Test
    void literalKeysInSourcesExist() throws IOException {
        Set<String> keys = lang("ru_ru").keySet();
        Pattern literal = Pattern.compile("\"(skirmish\\.analytics\\.[a-z_.]*[a-z_])\"");
        Set<String> missing = new TreeSet<>();
        Path root = Path.of("src/main/java/dev/skirmish/module/analytics");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = literal.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    String key = m.group(1);
                    if (!keys.contains(key) && !key.equals("skirmish.analytics.dossier.fights")) {
                        missing.add(key + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), missing.toString());
    }

    @Test
    void themeHasTheTokens() throws IOException {
        Theme theme = Theme.get();
        for (String color : List.of("fa_crit", "fa_good_16", "fa_bad_16", "fa_neutral_16", "fa_grid", "fa_reference")) {
            theme.color(color);
        }
        Pattern layout = Pattern.compile("\"(layout\\.analytics\\.[a-z_.]*[a-z_])\"|L \\+ \"([a-z_]+)\"");
        Pattern text = Pattern.compile("\"(fa_[a-z_]+)\"");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/dev/skirmish/module/analytics"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(file, StandardCharsets.UTF_8);
                Matcher prefix = Pattern.compile("String L = \"(layout\\.[a-z_.]+)\"").matcher(src);
                String l = prefix.find() ? prefix.group(1) : src.contains("ReviewCharts.L") ? "layout.analytics.review." : null;
                Matcher m = layout.matcher(src);
                while (m.find()) {
                    if (m.group(1) != null && !m.group(1).endsWith(".")) {
                        theme.num(m.group(1));
                    } else if (m.group(2) != null && l != null) {
                        theme.num(l + m.group(2));
                    }
                }
                Matcher t = text.matcher(src);
                while (t.find()) {
                    String key = t.group(1);
                    if (!key.startsWith("fa_crit") && !key.endsWith("_16") && !key.equals("fa_grid") && !key.equals("fa_reference")) {
                        theme.text(key);
                    }
                }
            }
        }
        try (InputStream in = AnalyticsModulesTest.class.getResourceAsStream("/assets/skirmish/theme.json")) {
            assertNotNull(in);
            assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("theme/analytics.json"));
        }
    }
}
