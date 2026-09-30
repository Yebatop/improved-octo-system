package dev.skirmish.module.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreboardLayoutTest {
    private static final int D = ScoreboardHud.DIVIDER;

    @Test
    void blankRunsBecomeOneDivider() {
        List<Boolean> blank = List.of(true, false, false, true, true, false, true);
        assertEquals(List.of(1, 2, D, 5), ScoreboardHud.layout(blank, true));
    }

    @Test
    void withoutDividersEveryRowStays() {
        List<Boolean> blank = List.of(true, false, true);
        assertEquals(List.of(0, 1, 2), ScoreboardHud.layout(blank, false));
    }

    @Test
    void allBlank() {
        assertEquals(List.of(), ScoreboardHud.layout(List.of(true, true), true));
    }
}
