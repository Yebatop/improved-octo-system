package dev.skirmish.module.playermenu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Text the player menu puts into the chat box (never sent: the user reviews it and presses Enter) and the nick
 * filter of the tab-list picker. HolyWorld commands from the wiki: {@code /ah player <ник>} (a player's auction
 * lots), {@code /pay <ник> <сумма>}, {@code /clan invite <ник>}; {@code /msg} is vanilla. Pure Java, unit tested.
 */
final class MenuCommands {
    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{1,16}");

    enum Command {
        AUCTION("/ah player %s"),
        PAY("/pay %s "),
        MESSAGE("/msg %s "),
        CLAN_INVITE("/clan invite %s");

        private final String template;

        Command(String template) {
            this.template = template;
        }

        /** The chat box text for {@code nick}; a trailing space where the user types the rest (amount, message). */
        String text(String nick) {
            return String.format(Locale.ROOT, template, nick);
        }
    }

    private MenuCommands() {
    }

    /** Only real nicks go into a command line (a tab-list name with spaces or codes would make a different command). */
    static boolean validNick(String nick) {
        return nick != null && NICK.matcher(nick).matches();
    }

    /**
     * Nicks containing {@code query} (ignoring case), without {@code self} and invalid names, those starting with the
     * query first, then alphabetical.
     */
    static List<String> filter(Collection<String> names, String query, String self) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (!validNick(name) || name.equalsIgnoreCase(self) || out.stream().anyMatch(name::equalsIgnoreCase)) {
                continue;
            }
            if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(name);
            }
        }
        out.sort((a, b) -> {
            boolean pa = a.toLowerCase(Locale.ROOT).startsWith(q);
            boolean pb = b.toLowerCase(Locale.ROOT).startsWith(q);
            if (pa != pb) {
                return pa ? -1 : 1;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(a, b);
        });
        return out;
    }
}
