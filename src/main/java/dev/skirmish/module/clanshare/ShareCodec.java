package dev.skirmish.module.clanshare;

import org.jspecify.annotations.Nullable;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The chat form of a share and everything around it that needs no Minecraft types: building the line to send,
 * finding tokens in received text and opening them.
 * <pre>
 * token   = "~cs" version length payload
 * version = "1"
 * length  = 2 alphabet chars: byte count B of the sealed payload (d0 * 30 + d1)
 * payload = Base30(nonce ‖ AES-GCM ciphertext ‖ tag), exactly Base30.encodedLength(B) chars
 * </pre>
 * The reader lower-cases ASCII before matching, so servers that capitalize or lower-case messages do not break it.
 */
final class ShareCodec {
    static final String MARKER = "~cs";
    static final char VERSION = '1';
    static final int MAX_CHAT_LENGTH = 256;
    static final int MAX_PREFIX_LENGTH = 16;
    static final int HEADER_CHARS = MARKER.length() + 1 + 2;
    static final int MIN_SEALED_BYTES = ShareCrypto.OVERHEAD + SharePayload.FIXED_BYTES + 2;
    static final int MAX_RUN = 2;
    static final int MAX_ATTEMPTS = 64;
    static final String SHARE_COMMAND = ".share";
    /** An alphabet run this long without a marker is probably a share whose marker the server mangled. */
    static final int SUSPICIOUS_RUN = 40;

    private static final List<String> ALWAYS_FORBIDDEN = List.of(".", "http", "www", "://");

    enum Failure {
        UNSUPPORTED_VERSION("unsupported format version"),
        BAD_LENGTH("bad length header"),
        TRUNCATED("truncated"),
        BAD_ENCODING("bad base30 encoding"),
        AUTH_FAILED("auth tag mismatch: wrong password or tampered message"),
        MALFORMED("decrypted but the layout is broken (other mod version?)");

        final String description;

        Failure(String description) {
            this.description = description;
        }
    }

    /**
     * A marker occurrence in a line of text. {@code start}/{@code end} are char offsets in that text;
     * {@code sealed} is set when the structure is intact, otherwise {@code failure} and {@code detail} say why.
     */
    record Token(int start, int end, byte @Nullable [] sealed, @Nullable Failure failure, String detail) {
        boolean intact() {
            return sealed != null;
        }
    }

    sealed interface Opened permits Opened.Ok, Opened.Rejected {
        record Ok(SharePayload payload, Token token) implements Opened {
        }

        record Rejected(Failure failure, String detail, Token token) implements Opened {
        }
    }

    /**
     * @param line      exactly what goes into the chat packet (prefix + token), at most {@value #MAX_CHAT_LENGTH} chars
     * @param sent      the payload as the reader will see it (name/nick possibly shortened)
     * @param sealedBytes nonce + ciphertext + tag
     * @param attempts  1 + re-encryptions because the text contained a forbidden pattern
     */
    record Encoded(String line, String token, SharePayload sent, int sealedBytes, int attempts) {
    }

    private ShareCodec() {
    }

    /** The name after {@code .share}, or null when the chat message is not a share request. */
    static @Nullable String parseShareCommand(String message) {
        String text = message.strip();
        if (!text.regionMatches(true, 0, SHARE_COMMAND, 0, SHARE_COMMAND.length())) {
            return null;
        }
        if (text.length() == SHARE_COMMAND.length()) {
            return "";
        }
        return Character.isWhitespace(text.charAt(SHARE_COMMAND.length())) ? text.substring(SHARE_COMMAND.length()).strip() : null;
    }

    /** The chat prefix without characters the server would reject, at most {@value #MAX_PREFIX_LENGTH} chars. */
    static String cleanPrefix(String prefix) {
        StringBuilder out = new StringBuilder(prefix.length());
        for (int i = 0; i < prefix.length() && out.length() < MAX_PREFIX_LENGTH; i++) {
            char c = prefix.charAt(i);
            if (c >= 32 && c != 127 && c != '§') {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Comma/space separated setting value → lower-case fragments. */
    static List<String> parseBlocked(String setting) {
        List<String> result = new ArrayList<>();
        for (String part : setting.split("[,;\\s]+")) {
            String fragment = part.strip().toLowerCase(Locale.ROOT);
            if (!fragment.isEmpty()) {
                result.add(fragment);
            }
        }
        return result;
    }

    /** Largest sealed byte count whose token fits after a prefix of {@code prefixLength} chars. */
    static int maxSealedBytes(int prefixLength) {
        int available = MAX_CHAT_LENGTH - prefixLength - HEADER_CHARS;
        int bytes = 0;
        while (bytes < Base30.MAX_BYTES && Base30.encodedLength(bytes + 1) <= available) {
            bytes++;
        }
        return bytes;
    }

    /**
     * Seals and encodes a share. Re-encrypts with a fresh nonce while the token contains a forbidden pattern
     * ({@link #isSafe}).
     *
     * @throws IllegalArgumentException when the payload cannot be serialized
     * @throws IllegalStateException    when no safe encoding was found in {@value #MAX_ATTEMPTS} attempts
     */
    static Encoded encode(SharePayload payload, SecretKey key, String chatPrefix, Collection<String> blocked, SecureRandom random) {
        String prefix = cleanPrefix(chatPrefix);
        byte[] plaintext = payload.toBytes(maxSealedBytes(prefix.length()) - ShareCrypto.OVERHEAD);
        SharePayload sent = SharePayload.fromBytes(plaintext);
        if (sent == null) {
            throw new IllegalArgumentException("Payload does not survive its own round trip");
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            byte[] sealed = ShareCrypto.seal(key, plaintext, random);
            String token = tokenFor(sealed);
            if (isSafe(token, blocked)) {
                return new Encoded(prefix + token, token, sent, sealed.length, attempt);
            }
        }
        throw new IllegalStateException("No safe encoding in " + MAX_ATTEMPTS + " attempts, check the blocked fragments");
    }

    static String tokenFor(byte[] sealed) {
        int bytes = sealed.length;
        return MARKER + VERSION + Base30.symbol(bytes / Base30.RADIX) + Base30.symbol(bytes % Base30.RADIX) + Base30.encode(sealed);
    }

    /**
     * No '.', "http", "www", "://", no run of more than {@value #MAX_RUN} equal characters, only printable ASCII
     * without spaces, none of the {@code blocked} fragments (case-insensitive).
     */
    static boolean isSafe(String token, Collection<String> blocked) {
        String lower = token.toLowerCase(Locale.ROOT);
        int run = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c <= ' ' || c >= 127) {
                return false;
            }
            run = i > 0 && lower.charAt(i - 1) == lower.charAt(i) ? run + 1 : 1;
            if (run > MAX_RUN) {
                return false;
            }
        }
        for (String fragment : ALWAYS_FORBIDDEN) {
            if (lower.contains(fragment)) {
                return false;
            }
        }
        for (String fragment : blocked) {
            if (!fragment.isEmpty() && lower.contains(fragment.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /** Every marker occurrence in {@code text}, in order. Case-insensitive; the rest of the line is ignored. */
    static List<Token> find(String text) {
        List<Token> tokens = new ArrayList<>();
        int from = 0;
        int start;
        while ((start = indexOfMarker(text, from)) >= 0) {
            Token token = parseAt(text, start);
            tokens.add(token);
            from = Math.max(token.end(), start + MARKER.length());
        }
        return tokens;
    }

    private static int indexOfMarker(String text, int from) {
        for (int i = from; i + MARKER.length() <= text.length(); i++) {
            if (text.regionMatches(true, i, MARKER, 0, MARKER.length())) {
                return i;
            }
        }
        return -1;
    }

    private static Token parseAt(String text, int start) {
        int pos = start + MARKER.length();
        if (pos >= text.length() || !Base30.isDigit(text.charAt(pos))) {
            return new Token(start, pos, null, Failure.TRUNCATED, "marker without version" + stopReason(text, pos));
        }
        char version = Character.toLowerCase(text.charAt(pos));
        int runEnd = runEnd(text, pos);
        if (version != VERSION) {
            return new Token(start, runEnd, null, Failure.UNSUPPORTED_VERSION, "version '" + version + "', this client reads '" + VERSION + "'");
        }
        int lengthAt = pos + 1;
        if (runEnd - lengthAt < 2) {
            return new Token(start, runEnd, null, looksCut(text, runEnd) ? Failure.TRUNCATED : Failure.BAD_ENCODING,
                    "length header incomplete" + stopReason(text, runEnd));
        }
        int bytes = Base30.digit(text.charAt(lengthAt)) * Base30.RADIX + Base30.digit(text.charAt(lengthAt + 1));
        if (bytes < MIN_SEALED_BYTES || bytes > Base30.MAX_BYTES) {
            return new Token(start, runEnd, null, Failure.BAD_LENGTH, bytes + " bytes declared");
        }
        int payloadAt = lengthAt + 2;
        int expected = Base30.encodedLength(bytes);
        int available = runEnd - payloadAt;
        if (available < expected) {
            Failure failure = looksCut(text, runEnd) ? Failure.TRUNCATED : Failure.BAD_ENCODING;
            return new Token(start, runEnd, null, failure, "expected " + expected + " payload chars, got " + available + stopReason(text, runEnd));
        }
        int end = payloadAt + expected;
        byte[] sealed = Base30.decode(text.substring(payloadAt, end), bytes);
        if (sealed == null) {
            return new Token(start, end, null, Failure.BAD_ENCODING, "value does not fit into " + bytes + " bytes");
        }
        return new Token(start, end, sealed, null, bytes + " bytes, " + (end - start) + " chars" + (available > expected ? ", " + (available - expected) + " trailing chars ignored" : ""));
    }

    private static int runEnd(String text, int from) {
        int end = from;
        while (end < text.length() && Base30.isDigit(text.charAt(end))) {
            end++;
        }
        return end;
    }

    /** End of line, whitespace or an ellipsis/period a server appends after cutting; anything else is mangling. */
    private static boolean looksCut(String text, int at) {
        if (at >= text.length()) {
            return true;
        }
        char c = text.charAt(at);
        return Character.isWhitespace(c) || c == '.' || c == '…';
    }

    private static String stopReason(String text, int at) {
        if (at >= text.length()) {
            return " (line ends there)";
        }
        char c = text.charAt(at);
        return String.format(Locale.ROOT, " (stopped at '%s' U+%04X)", Character.isISOControl(c) ? "?" : String.valueOf(c), (int) c);
    }

    static Opened open(Token token, SecretKey key) {
        byte[] sealed = token.sealed();
        if (sealed == null) {
            return new Opened.Rejected(token.failure() == null ? Failure.BAD_ENCODING : token.failure(), token.detail(), token);
        }
        byte[] plaintext = ShareCrypto.open(key, sealed);
        if (plaintext == null) {
            return new Opened.Rejected(Failure.AUTH_FAILED, token.detail(), token);
        }
        SharePayload payload = SharePayload.fromBytes(plaintext);
        if (payload == null) {
            return new Opened.Rejected(Failure.MALFORMED, plaintext.length + " plaintext bytes", token);
        }
        return new Opened.Ok(payload, token);
    }

    /** True when {@code maxAgeSeconds} is 0 (off) or the share's timestamp is within ±maxAge of now. */
    static boolean isFresh(SharePayload payload, long nowSeconds, long maxAgeSeconds) {
        return maxAgeSeconds <= 0 || Math.abs(nowSeconds - payload.time()) <= maxAgeSeconds;
    }

    /** Length of the longest run of alphabet characters, to spot a share whose marker was stripped. */
    static int longestAlphabetRun(String text) {
        int longest = 0;
        int i = 0;
        while (i < text.length()) {
            int end = runEnd(text, i);
            longest = Math.max(longest, end - i);
            i = end + 1;
        }
        return longest;
    }
}
