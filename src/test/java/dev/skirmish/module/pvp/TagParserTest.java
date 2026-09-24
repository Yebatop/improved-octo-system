package dev.skirmish.module.pvp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The formats below are guesses (HolyWorld does not publish them); they pin down how tolerant the parser is. */
class TagParserTest {
    private static final List<String> PEACE_BOARD = List.of(
            "§7Лайт-Анархия #12 -◆-",
            "§fНик: §bMe_Player",
            "§fБаланс: §a12 500$",
            "§f",
            "§7mc.holyworld.ru");

    private static TagParser.Reading board(String title, String... lines) {
        return TagParser.parse(title, List.of(lines), List.of(), "Me_Player").reading();
    }

    @Test
    void peacefulBoardHasNoTag() {
        TagParser.Result result = TagParser.parse("§6§lHOLYWORLD", PEACE_BOARD, List.of(), "Me_Player");
        assertNull(result.reading());
        assertTrue(result.unrecognized().isEmpty(), result.unrecognized().toString());
    }

    @Test
    void timerLineInSeveralSpellings() {
        String[] lines = {"§cРежим PvP: §f15с", "§cᴘᴠᴘ §f15", "§cКТ: 0:15", "§c§lПВП §r15 сек", "KT 15s",
                "§cВы в бою! §f15 сек.", "До выхода: 15 секунд", "Combat: 15s"};
        for (String line : lines) {
            TagParser.Reading r = board("HolyWorld", line);
            assertNotNull(r, line);
            assertEquals(15, r.seconds(), line);
            assertEquals(TagParser.Source.BOARD, r.source(), line);
        }
    }

    @Test
    void bossFightIsNotCombatTag() {
        assertNull(board("HolyWorld", "Бой с боссом: 5:00"));
        assertNotNull(board("HolyWorld", "Бой: 12с"));
    }

    @Test
    void opponentsBelowTheTimer() {
        TagParser.Reading r = board("§6HolyWorld",
                "§7Лайт-Анархия #12 -◆-",
                "§cРежим PvP: §f18с",
                "§f• GFk31AK §7- 18с",
                "§f• Notch_ §7(4с)",
                "§f",
                "§fБаланс: §a100$");
        assertNotNull(r);
        assertEquals(18, r.seconds());
        assertEquals(List.of("GFk31AK", "Notch_"), r.opponents());
    }

    @Test
    void headerThenOpponentTimesWithoutOwnTimer() {
        TagParser.Reading r = board("HolyWorld",
                "§c§lᴘᴠᴘ",
                "§fGFk31AK 12",
                "§fNotch_ 7",
                "§f---------");
        assertNotNull(r);
        assertEquals(12, r.seconds());
        assertEquals(List.of("GFk31AK", "Notch_"), r.opponents());
    }

    @Test
    void remainingLineUnderHeader() {
        TagParser.Reading r = board("HolyWorld", "§cРежим PvP", "§fОсталось: §e9 сек", "§fПротивники:", "§fGFk31AK");
        assertNotNull(r);
        assertEquals(9, r.seconds());
        assertEquals(List.of("GFk31AK"), r.opponents());
    }

    @Test
    void pvpTitleOpensTheBlock() {
        TagParser.Reading r = board("§c§lPvP", "§fGFk31AK §7» §f11с", "§fDream §7» §f6с");
        assertNotNull(r);
        assertEquals(11, r.seconds());
        assertEquals(List.of("GFk31AK", "Dream"), r.opponents());
    }

    @Test
    void ownNameAndLabelledLinesAreNotOpponents() {
        TagParser.Reading r = board("HolyWorld", "§cPvP: 10с", "§fMe_Player", "§fНик: Someone", "§fEnemy_1");
        assertNotNull(r);
        assertEquals(List.of("Enemy_1"), r.opponents());
    }

    @Test
    void bossBarWithTime() {
        TagParser.Result result = TagParser.parse("", List.of(),
                List.of(new TagParser.BossBar("Wither", 0.5f), new TagParser.BossBar("§cᴘᴠᴘ §f- §e13 сек.", 0.65f)), "Me");
        TagParser.Reading r = result.reading();
        assertNotNull(r);
        assertEquals(13, r.seconds());
        assertEquals(TagParser.Source.BOSS_BAR, r.source());
    }

    @Test
    void bossBarWithoutTimeUsesProgress() {
        TagParser.Result result = TagParser.parse("", List.of(), List.of(new TagParser.BossBar("Режим PvP", 0.4f)), "Me");
        TagParser.Reading r = result.reading();
        assertNotNull(r);
        assertEquals(-1, r.seconds());
        assertEquals(0.4f, r.progress(), 1e-6);
        assertFalse(result.unrecognized().isEmpty());
    }

    @Test
    void boardOpponentsWithBossBarTimer() {
        TagParser.Result result = TagParser.parse("", List.of("§cПротивники:", "§fGFk31AK"),
                List.of(new TagParser.BossBar("PvP 8с", 0.4f)), "Me");
        TagParser.Reading r = result.reading();
        assertNotNull(r);
        assertEquals(8, r.seconds());
        assertEquals(List.of("GFk31AK"), r.opponents());
    }

    @Test
    void unrecognizedCandidatesAreReported() {
        TagParser.Result result = TagParser.parse("HolyWorld",
                List.of("§fДо рестарта: 1:30", "§cPvP 10 из 20"), List.of(), "Me");
        assertNull(result.reading());
        assertEquals(List.of("§fДо рестарта: 1:30", "§cPvP 10 из 20"), result.unrecognized());
    }
}
