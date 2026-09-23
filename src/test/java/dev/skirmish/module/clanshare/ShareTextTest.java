package dev.skirmish.module.clanshare;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShareTextTest {
    @Test
    void replacementKeepsTextAroundTheToken() {
        Component line = Component.empty()
                .append(Component.literal("[VIP] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Steve").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ~cs1ab").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("cd tail"));
        ShareText.Flat flat = new ShareText.Flat(line);
        assertEquals("[VIP] Steve: ~cs1abcd tail", flat.plain());
        int start = flat.plain().indexOf("~cs");
        int end = flat.plain().indexOf(" tail");
        Component replaced = flat.replace(start, end, Component.literal("LABEL"));
        assertEquals("[VIP] Steve: LABEL tail", replaced.getString());
        assertEquals("LABEL", flat.replace(0, flat.plain().length(), Component.literal("LABEL")).getString());
        assertEquals("[VIP] Steve: ~cs1abcd tailLABEL", flat.replace(flat.plain().length(), flat.plain().length(), Component.literal("LABEL")).getString());
    }
}
