package dev.skirmish.module.alerts.parse;

import dev.skirmish.module.market.parse.HwText;
import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Lite region alert ("Уникальный приват" with the «Оповещение» effect), confirmed verbatim by the official
 * channel: {@code Осторожно! В один из ваших регионов вторгся <ник>.} Tolerates colors, decorations, a missing
 * "!", a prefix before the line and feminine/plural verb forms. Pure Java (unit tested).
 */
public final class IntrusionLine {
    private static final Pattern LINE = Pattern.compile(
            "осторожно\\s*!?\\s*в\\s+(?:один\\s+из\\s+)?ваш(?:их|ем|ий)?\\s+регион(?:ов|е)?\\s+вторг(?:ся|лась|лись|лось)\\s+(?:игрок\\s+)?");
    private static final Pattern NICK = Pattern.compile("[\\p{L}\\p{N}_.\\-]+");

    private IntrusionLine() {
    }

    /** The intruder's nick, or null when the line is not the alert. */
    public static @Nullable String intruder(String line) {
        String plain = HwText.plain(line);
        Matcher m = LINE.matcher(HwText.key(plain));
        if (!m.find()) {
            return null;
        }
        Matcher nick = NICK.matcher(plain.substring(m.end()));
        if (!nick.lookingAt()) {
            return null;
        }
        String name = nick.group().replaceAll("[.\\-]+$", "");
        return name.isEmpty() ? null : name;
    }
}
