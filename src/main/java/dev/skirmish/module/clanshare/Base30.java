package dev.skirmish.module.clanshare;

import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/**
 * Fixed-length radix-30 text encoding of a byte array over lower-case consonants and digits. No Minecraft types.
 * <p>
 * The alphabet has no vowels (so random output does not spell words a chat filter would censor), no upper case
 * (anti-caps plugins lower-case long messages) and no punctuation (no '.', so nothing looks like a domain).
 * The output length depends only on the byte count: {@code n} bytes always take {@link #encodedLength(int)} chars,
 * which lets the reader tell a truncated message from a corrupted one.
 */
final class Base30 {
    static final String ALPHABET = "0123456789bcdfghjklmnpqrstvwxz";
    static final int RADIX = 30;
    static final int MAX_BYTES = 255;

    private static final int[] LENGTHS = new int[MAX_BYTES + 1];

    static {
        for (int bytes = 1; bytes <= MAX_BYTES; bytes++) {
            LENGTHS[bytes] = BigInteger.ONE.shiftLeft(8 * bytes).subtract(BigInteger.ONE).toString(RADIX).length();
        }
    }

    private Base30() {
    }

    /** Characters used for {@code bytes} bytes (digits of 256^bytes - 1 in base 30). */
    static int encodedLength(int bytes) {
        if (bytes < 0 || bytes > MAX_BYTES) {
            throw new IllegalArgumentException("Unsupported length " + bytes);
        }
        return LENGTHS[bytes];
    }

    /** Value of an alphabet character (ASCII case-insensitive), or -1. */
    static int digit(char c) {
        char lower = c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c;
        return ALPHABET.indexOf(lower);
    }

    static boolean isDigit(char c) {
        return digit(c) >= 0;
    }

    static char symbol(int value) {
        return ALPHABET.charAt(value);
    }

    static String encode(byte[] data) {
        if (data.length == 0) {
            throw new IllegalArgumentException("Nothing to encode");
        }
        int length = encodedLength(data.length);
        String digits = new BigInteger(1, data).toString(RADIX);
        StringBuilder out = new StringBuilder(length);
        for (int i = digits.length(); i < length; i++) {
            out.append(ALPHABET.charAt(0));
        }
        for (int i = 0; i < digits.length(); i++) {
            out.append(ALPHABET.charAt(Character.digit(digits.charAt(i), RADIX)));
        }
        return out.toString();
    }

    /**
     * Decodes exactly {@link #encodedLength(int) encodedLength(bytes)} characters; null when a character is outside
     * the alphabet, the length is wrong or the value does not fit into {@code bytes} bytes.
     */
    static byte @Nullable [] decode(CharSequence text, int bytes) {
        if (bytes < 1 || bytes > MAX_BYTES || text.length() != LENGTHS[bytes]) {
            return null;
        }
        StringBuilder digits = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            int value = digit(text.charAt(i));
            if (value < 0) {
                return null;
            }
            digits.append(Character.forDigit(value, RADIX));
        }
        BigInteger value = new BigInteger(digits.toString(), RADIX);
        if (value.bitLength() > 8 * bytes) {
            return null;
        }
        byte[] raw = value.toByteArray();
        byte[] out = new byte[bytes];
        int copy = Math.min(raw.length, bytes);
        System.arraycopy(raw, raw.length - copy, out, bytes - copy, copy);
        return out;
    }
}
