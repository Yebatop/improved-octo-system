package dev.skirmish.debug;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugLogTest {
    @AfterEach
    void restoreDefaults() {
        DebugLog.configureRotation(2L * 1024 * 1024, 3);
    }

    @Test
    void linesHaveTimeAndTag(@TempDir Path dir) throws Exception {
        DebugLog.initForTests(dir);
        DebugLog.log("killcam", "hello");
        DebugLog.log("clanshare", "world");
        DebugLog.flush();
        List<String> lines = Files.readAllLines(dir.resolve("debug.log"));
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} \\[killcam] hello"), lines.get(0));
        assertTrue(lines.get(1).endsWith("[clanshare] world"));
    }

    @Test
    void rotatesBySize(@TempDir Path dir) throws Exception {
        DebugLog.initForTests(dir);
        DebugLog.configureRotation(1024, 3);
        for (int round = 0; round < 4; round++) {
            for (int i = 0; i < 40; i++) {
                DebugLog.log("core", "round " + round + " line " + i + " " + "x".repeat(20));
            }
            DebugLog.flush();
        }
        assertTrue(Files.exists(dir.resolve("debug.log")));
        assertTrue(Files.exists(dir.resolve("debug.1.log")));
        assertTrue(Files.exists(dir.resolve("debug.2.log")));
        assertTrue(Files.notExists(dir.resolve("debug.3.log")), "only keepFiles files are kept");
    }
}
