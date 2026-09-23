package dev.skirmish.module.clanshare;

import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * What a share carries, and its binary layout (version 1, big endian, before encryption). No Minecraft types.
 * <pre>
 * u32  time       unix seconds when it was sent
 * i32  x          block X
 * i32  z          block Z
 * i16  y          block Y (clamped)
 * u8   dimension  0 overworld, 1 the_nether, 2 the_end, 255 = custom id follows
 * [u8 n + n bytes UTF-8 dimension id]   only for 255
 * u8 n + n bytes  UTF-8 nick (1..{@value #MAX_NICK_BYTES})
 * rest            UTF-8 name (may be empty: the reader shows a default)
 * </pre>
 */
record SharePayload(long time, int x, int y, int z, String dimension, String nick, String name) {
    static final int FIXED_BYTES = 4 + 4 + 4 + 2 + 1;
    static final int MAX_NICK_BYTES = 32;
    static final int MAX_DIMENSION_BYTES = 48;
    static final int MAX_NAME_CHARS = 32;
    static final int CUSTOM_DIMENSION = 255;
    static final List<String> KNOWN_DIMENSIONS = List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");

    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    SharePayload {
        nick = cleanNick(nick);
        name = cleanName(name);
    }

    static boolean isValidDimension(String dimension) {
        return dimension.length() <= MAX_DIMENSION_BYTES && DIMENSION.matcher(dimension).matches();
    }

    /** Printable text without formatting codes, trimmed, at most {@value #MAX_NAME_CHARS} code points. */
    static String cleanName(String name) {
        StringBuilder out = new StringBuilder(name.length());
        name.codePoints().filter(SharePayload::isPrintable).forEach(out::appendCodePoint);
        String trimmed = out.toString().strip();
        return trimmed.codePointCount(0, trimmed.length()) > MAX_NAME_CHARS
                ? trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_NAME_CHARS)).strip()
                : trimmed;
    }

    /** One word: whitespace, control characters and '§' become '_'. */
    static String cleanNick(String nick) {
        StringBuilder out = new StringBuilder(nick.length());
        nick.strip().codePoints().forEach(c -> out.appendCodePoint(isPrintable(c) && !Character.isWhitespace(c) && !Character.isSpaceChar(c) ? c : '_'));
        return utf8Prefix(out.toString(), MAX_NICK_BYTES);
    }

    private static boolean isPrintable(int c) {
        int type = Character.getType(c);
        return c != '§' && !Character.isISOControl(c)
                && type != Character.FORMAT && type != Character.SURROGATE && type != Character.UNASSIGNED;
    }

    /** Longest prefix of whole code points that fits into {@code maxBytes} UTF-8 bytes. */
    static String utf8Prefix(String text, int maxBytes) {
        int bytes = 0;
        int end = 0;
        while (end < text.length()) {
            int c = text.codePointAt(end);
            int size = c < 0x80 ? 1 : c < 0x800 ? 2 : c < 0x10000 ? 3 : 4;
            if (bytes + size > maxBytes) {
                break;
            }
            bytes += size;
            end += Character.charCount(c);
        }
        return text.substring(0, end);
    }

    /**
     * Serializes the payload, cutting the name so the result fits into {@code maxBytes}.
     *
     * @throws IllegalArgumentException when the dimension is not a valid id or even an empty name does not fit
     */
    byte[] toBytes(int maxBytes) {
        if (nick.isEmpty()) {
            throw new IllegalArgumentException("Empty nick");
        }
        if (!isValidDimension(dimension)) {
            throw new IllegalArgumentException("Invalid dimension id " + dimension);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(64);
        writeInt(out, (int) time);
        writeInt(out, x);
        writeInt(out, z);
        int clampedY = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, y));
        out.write(clampedY >> 8);
        out.write(clampedY);
        int code = KNOWN_DIMENSIONS.indexOf(dimension);
        if (code >= 0) {
            out.write(code);
        } else {
            out.write(CUSTOM_DIMENSION);
            writeShortString(out, dimension);
        }
        writeShortString(out, nick);
        int room = maxBytes - out.size();
        if (room < 0) {
            throw new IllegalArgumentException("Payload header needs " + out.size() + " bytes, only " + maxBytes + " available");
        }
        out.writeBytes(utf8Prefix(name, room).strip().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** Parses {@link #toBytes} output; null when the layout is broken. */
    static @Nullable SharePayload fromBytes(byte[] data) {
        ByteBuffer in = ByteBuffer.wrap(data);
        if (in.remaining() < FIXED_BYTES + 2) {
            return null;
        }
        long time = in.getInt() & 0xFFFFFFFFL;
        int x = in.getInt();
        int z = in.getInt();
        int y = in.getShort();
        int code = in.get() & 0xFF;
        String dimension;
        if (code < KNOWN_DIMENSIONS.size()) {
            dimension = KNOWN_DIMENSIONS.get(code);
        } else if (code == CUSTOM_DIMENSION) {
            dimension = readShortString(in, MAX_DIMENSION_BYTES);
            if (dimension == null || !isValidDimension(dimension)) {
                return null;
            }
        } else {
            return null;
        }
        String nick = readShortString(in, MAX_NICK_BYTES);
        if (nick == null || nick.isEmpty()) {
            return null;
        }
        String name = readUtf8(in, in.remaining());
        if (name == null) {
            return null;
        }
        SharePayload payload = new SharePayload(time, x, y, z, dimension, nick, name);
        return payload.nick().isEmpty() ? null : payload;
    }

    /** Arguments of {@code /skirmish-clanshare add}: {@code x y z dimension nick [name...]}. */
    String toCommandArgs() {
        return "add " + x + " " + y + " " + z + " " + dimension + " " + nick + (name.isEmpty() ? "" : " " + name);
    }

    static @Nullable SharePayload fromCommandArgs(String args) {
        String[] parts = args.strip().split(" ", 7);
        if (parts.length < 6 || !parts[0].equals("add")) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            if (!isValidDimension(parts[4])) {
                return null;
            }
            SharePayload payload = new SharePayload(0, x, y, z, parts[4], parts[5], parts.length > 6 ? parts[6] : "");
            return payload.nick().isEmpty() ? null : payload;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    String coordsText() {
        return x + " " + y + " " + z;
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private static void writeShortString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(bytes.length);
        out.writeBytes(bytes);
    }

    private static @Nullable String readShortString(ByteBuffer in, int maxBytes) {
        if (!in.hasRemaining()) {
            return null;
        }
        int length = in.get() & 0xFF;
        if (length > maxBytes || length > in.remaining()) {
            return null;
        }
        return readUtf8(in, length);
    }

    private static @Nullable String readUtf8(ByteBuffer in, int length) {
        ByteBuffer slice = in.slice(in.position(), length);
        in.position(in.position() + length);
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(slice).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
