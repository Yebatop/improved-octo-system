package dev.skirmish.module.playermenu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuCommandsTest {
    @Test
    void commandsAreOnlyTypedIn() {
        assertEquals("/ah player Enemy_3", MenuCommands.Command.AUCTION.text("Enemy_3"));
        assertEquals("/pay Enemy_3 ", MenuCommands.Command.PAY.text("Enemy_3"));
        assertEquals("/msg Enemy_3 ", MenuCommands.Command.MESSAGE.text("Enemy_3"));
        assertEquals("/clan invite Enemy_3", MenuCommands.Command.CLAN_INVITE.text("Enemy_3"));
    }

    @Test
    void nicks() {
        assertTrue(MenuCommands.validNick("Abc_12"));
        assertFalse(MenuCommands.validNick("two words"));
        assertFalse(MenuCommands.validNick("§cRed"));
        assertFalse(MenuCommands.validNick("a".repeat(17)));
        assertFalse(MenuCommands.validNick(""));
    }

    @Test
    void pickerFilter() {
        List<String> names = List.of("zeta", "Alpha", "beta_Al", "Me", "bad name", "alpha");
        assertEquals(List.of("Alpha", "beta_Al", "zeta"), MenuCommands.filter(names, "", "me"));
        // Prefix matches first.
        assertEquals(List.of("Alpha", "beta_Al"), MenuCommands.filter(names, "al", "Me"));
        assertEquals(List.of(), MenuCommands.filter(names, "xyz", "Me"));
    }
}
