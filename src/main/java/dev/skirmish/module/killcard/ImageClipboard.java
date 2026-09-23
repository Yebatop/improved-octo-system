package dev.skirmish.module.killcard;

import org.jspecify.annotations.Nullable;

import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Puts a saved card PNG on the system clipboard. Called on the card thread only, never on the render thread.
 * <p>
 * Minecraft's {@code Main} forces {@code java.awt.headless=true}, so the AWT clipboard is normally unavailable, and
 * AWT must not be initialised next to GLFW on macOS at all (both want the AppKit main thread). GLFW's clipboard is
 * text-only. Therefore the image is handed to the OS clipboard tool in a separate process:
 * macOS {@code osascript}, Windows {@code powershell -STA} (Windows Forms {@code Clipboard.SetImage}), Linux
 * {@code wl-copy} (Wayland) or {@code xclip} (X11). The AWT clipboard is used only when something else already turned
 * headless mode off and the OS is not macOS. The path is passed as an argument or environment variable, never
 * spliced into a script.
 */
public final class ImageClipboard {
    private static final long TIMEOUT_SECONDS = 10;
    private static final int MAX_OUTPUT = 300;

    /** @param method how the copy was attempted ({@code osascript}, {@code awt}, ...), or {@code none} */
    public record Result(boolean success, String method, String detail, long millis) {
    }

    enum Os {
        WINDOWS, MAC, LINUX, OTHER;

        static Os current() {
            String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (name.contains("mac") || name.contains("darwin")) {
                return MAC;
            }
            if (name.startsWith("windows")) {
                return WINDOWS;
            }
            if (name.contains("linux") || name.contains("bsd")) {
                return LINUX;
            }
            return OTHER;
        }
    }

    private ImageClipboard() {
    }

    public static Result copy(Path png, BufferedImage image) {
        long start = System.nanoTime();
        Result result;
        try {
            result = copyUnchecked(png, image, Os.current());
        } catch (Throwable t) {
            result = new Result(false, "error", t.toString(), 0);
        }
        return new Result(result.success(), result.method(), result.detail(), (System.nanoTime() - start) / 1_000_000);
    }

    private static Result copyUnchecked(Path png, BufferedImage image, Os os) throws Exception {
        String path = png.toAbsolutePath().toString();
        if (os == Os.MAC) {
            return run("osascript", List.of(executable("osascript", "/usr/bin/osascript"),
                    "-e", "on run argv",
                    "-e", "set the clipboard to (read (POSIX file (item 1 of argv)) as «class PNGf»)",
                    "-e", "end run", path), Map.of(), null);
        }
        if (!GraphicsEnvironment.isHeadless()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageSelection(image), null);
            return new Result(true, "awt", "system clipboard", 0);
        }
        if (os == Os.WINDOWS) {
            String systemRoot = System.getenv().getOrDefault("SystemRoot", "C:\\Windows");
            String powershell = executable("powershell.exe", systemRoot + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe");
            String script = "Add-Type -AssemblyName System.Windows.Forms; Add-Type -AssemblyName System.Drawing; "
                    + "$img = [System.Drawing.Image]::FromFile($env:SKIRMISH_KILLCARD_PNG); "
                    + "try { [System.Windows.Forms.Clipboard]::SetImage($img) } finally { $img.Dispose() }";
            return run("powershell", List.of(powershell, "-NoProfile", "-NonInteractive", "-STA", "-WindowStyle", "Hidden",
                    "-Command", script), Map.of("SKIRMISH_KILLCARD_PNG", path), null);
        }
        String wayland = System.getenv("WAYLAND_DISPLAY");
        String display = System.getenv("DISPLAY");
        String wlCopy = findOnPath("wl-copy");
        String xclip = findOnPath("xclip");
        if (wayland != null && !wayland.isBlank() && wlCopy != null) {
            return run("wl-copy", List.of(wlCopy, "--type", "image/png"), Map.of(), png);
        }
        if (display != null && !display.isBlank() && xclip != null) {
            return run("xclip", List.of(xclip, "-selection", "clipboard", "-t", "image/png", "-i", path), Map.of(), null);
        }
        return new Result(false, "none", "java.awt.headless=true (set by Minecraft) and no clipboard tool found"
                + " (WAYLAND_DISPLAY=" + wayland + ", wl-copy=" + (wlCopy != null) + ", DISPLAY=" + display
                + ", xclip=" + (xclip != null) + ")", 0);
    }

    private static Result run(String method, List<String> command, Map<String, String> env, @Nullable Path stdin)
            throws IOException, InterruptedException {
        Path output = Files.createTempFile("skirmish-killcard-clipboard", ".txt");
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
            builder.redirectInput(stdin != null ? ProcessBuilder.Redirect.from(stdin.toFile()) : ProcessBuilder.Redirect.PIPE);
            builder.environment().putAll(env);
            Process process = builder.start();
            if (stdin == null) {
                process.getOutputStream().close();
            }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(false, method, "timed out after " + TIMEOUT_SECONDS + " s", 0);
            }
            int exit = process.exitValue();
            String text = readTail(output);
            return exit == 0
                    ? new Result(true, method, text.isEmpty() ? "exit 0" : text, 0)
                    : new Result(false, method, "exit code " + exit + (text.isEmpty() ? "" : ": " + text), 0);
        } finally {
            try {
                Files.deleteIfExists(output);
            } catch (IOException ignored) {
                // Windows keeps the file locked while a forked helper lives; the temp dir is cleaned by the OS.
            }
        }
    }

    private static String readTail(Path file) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).strip().replaceAll("\\s+", " ");
            return text.length() > MAX_OUTPUT ? text.substring(0, MAX_OUTPUT) + "…" : text;
        } catch (IOException e) {
            return "";
        }
    }

    private static String executable(String name, String fallback) {
        String found = findOnPath(name);
        return found != null ? found : fallback;
    }

    static @Nullable String findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir.trim(), name);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private record ImageSelection(Image image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
