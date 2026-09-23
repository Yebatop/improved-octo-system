package dev.skirmish.module.clanshare;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareCodecTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static SecretKey key;
    private static SecretKey otherKey;

    @BeforeAll
    static void deriveKeys() {
        key = ShareCrypto.deriveKey("our clan secret");
        otherKey = ShareCrypto.deriveKey("our clan secreT");
    }

    private static SecureRandom seeded(long seed) {
        try {
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(seed);
            return random;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static SharePayload payload(String nick, String name) {
        return new SharePayload(1_790_000_000L, -12345, 64, 987654, OVERWORLD, nick, name);
    }

    private static ShareCodec.Opened decodeFirst(String line, SecretKey with) {
        List<ShareCodec.Token> tokens = ShareCodec.find(line);
        assertEquals(1, tokens.size(), "exactly one marker expected in " + line);
        return ShareCodec.open(tokens.getFirst(), with);
    }

    private static SharePayload decodeOk(String line) {
        ShareCodec.Opened opened = decodeFirst(line, key);
        return assertInstanceOf(ShareCodec.Opened.Ok.class, opened, () -> "rejected: " + opened).payload();
    }

    private static ShareCodec.Failure decodeFailure(String line, SecretKey with) {
        ShareCodec.Opened opened = decodeFirst(line, with);
        return assertInstanceOf(ShareCodec.Opened.Rejected.class, opened).failure();
    }

    @Test
    void roundTripKeepsEverything() {
        List<SharePayload> cases = List.of(
                payload("Steve", "base"),
                payload("Alex_2008", ""),
                payload("Ник", "Дом у реки"),
                new SharePayload(0, 29_999_999, -64, -29_999_999, "minecraft:the_nether", "a", "portal hub"),
                new SharePayload(4_000_000_000L, 0, 320, 0, "minecraft:the_end", "b", "x"),
                new SharePayload(1, 1, 1, 1, "holyworld:arena_1", "PvPer", "arena"));
        SecureRandom random = seeded(1);
        for (SharePayload original : cases) {
            ShareCodec.Encoded encoded = ShareCodec.encode(original, key, "", List.of(), random);
            assertEquals(original, encoded.sent());
            assertEquals(original, decodeOk(encoded.line()));
        }
    }

    @Test
    void sameInputGivesDifferentCiphertexts() {
        SharePayload original = payload("Steve", "base");
        String first = ShareCodec.encode(original, key, "", List.of(), new SecureRandom()).token();
        String second = ShareCodec.encode(original, key, "", List.of(), new SecureRandom()).token();
        assertNotEquals(first, second);
    }

    @Test
    void wrongPasswordFailsAuthentication() {
        String line = ShareCodec.encode(payload("Steve", "base"), key, "", List.of(), seeded(2)).line();
        assertEquals(ShareCodec.Failure.AUTH_FAILED, decodeFailure(line, otherKey));
    }

    @Test
    void passwordIsNormalizedButCaseSensitive() {
        assertArrayEquals(key.getEncoded(), ShareCrypto.deriveKey("  our clan secret ").getEncoded());
        assertNotEquals(ShareCrypto.fingerprint(key), ShareCrypto.fingerprint(otherKey));
        assertEquals(8, ShareCrypto.fingerprint(key).length());
    }

    @Test
    void anyChangedCharacterIsRejected() {
        String token = ShareCodec.encode(payload("Steve", "base"), key, "", List.of(), seeded(3)).token();
        for (int i = ShareCodec.HEADER_CHARS; i < token.length(); i++) {
            char original = token.charAt(i);
            char replacement = Base30.symbol((Base30.digit(original) + 7) % Base30.RADIX);
            String corrupted = token.substring(0, i) + replacement + token.substring(i + 1);
            ShareCodec.Failure failure = decodeFailure(corrupted, key);
            assertTrue(failure == ShareCodec.Failure.AUTH_FAILED || failure == ShareCodec.Failure.BAD_ENCODING, "position " + i + ": " + failure);
        }
    }

    @Test
    void corruptedByteFailsAuthentication() {
        String token = ShareCodec.encode(payload("Steve", "base"), key, "", List.of(), seeded(4)).token();
        byte[] sealed = ShareCodec.find(token).getFirst().sealed();
        sealed[ShareCrypto.NONCE_BYTES + 3] ^= 0x01;
        assertEquals(ShareCodec.Failure.AUTH_FAILED, decodeFailure(ShareCodec.tokenFor(sealed), key));
    }

    @Test
    void foreignCharacterInsidePayloadIsBadEncoding() {
        String token = ShareCodec.encode(payload("Steve", "base"), key, "", List.of(), seeded(5)).token();
        int middle = token.length() / 2;
        String broken = token.substring(0, middle) + "a" + token.substring(middle + 1);
        assertEquals(ShareCodec.Failure.BAD_ENCODING, decodeFailure(broken, key));
    }

    @Test
    void truncatedLinesAreRejected() {
        String line = ShareCodec.encode(payload("Steve", "base"), key, "", List.of(), seeded(6)).line();
        for (int length = ShareCodec.MARKER.length(); length < line.length(); length++) {
            String cut = line.substring(0, length);
            assertEquals(ShareCodec.Failure.TRUNCATED, decodeFailure(cut, key), "cut at " + length);
            assertEquals(ShareCodec.Failure.TRUNCATED, decodeFailure(cut + "...", key), "cut at " + length + " with ellipsis");
        }
    }

    @Test
    void lineFitsChatLimitWithLongNamesAndNicks() {
        String longName = "Очень длинное название точки ".repeat(20);
        String longNick = "N".repeat(40);
        String longDimension = "somemod:" + "d".repeat(SharePayload.MAX_DIMENSION_BYTES - "somemod:".length());
        SecureRandom random = seeded(7);
        for (String prefix : List.of("", "!", "!".repeat(ShareCodec.MAX_PREFIX_LENGTH), "!".repeat(40))) {
            for (String dimension : List.of(OVERWORLD, longDimension)) {
                SharePayload original = new SharePayload(5, 1, 2, 3, dimension, longNick, longName);
                ShareCodec.Encoded encoded = ShareCodec.encode(original, key, prefix, List.of(), random);
                assertTrue(encoded.line().length() <= ShareCodec.MAX_CHAT_LENGTH, "length " + encoded.line().length());
                assertTrue(encoded.line().startsWith(prefix.substring(0, Math.min(prefix.length(), ShareCodec.MAX_PREFIX_LENGTH))));
                SharePayload decoded = decodeOk(encoded.line());
                assertEquals(encoded.sent(), decoded);
                assertEquals(SharePayload.MAX_NICK_BYTES, decoded.nick().length());
                assertTrue(longName.startsWith(decoded.name()), decoded.name());
                assertFalse(decoded.name().isEmpty());
                assertEquals(dimension, decoded.dimension());
            }
        }
    }

    @Test
    void multiByteNickIsCutOnCodePointBoundary() {
        SharePayload original = payload("Я".repeat(20), "");
        assertEquals(SharePayload.MAX_NICK_BYTES / 2, original.nick().length());
        assertEquals(original, decodeOk(ShareCodec.encode(original, key, "", List.of(), seeded(8)).line()));
        assertEquals("a_b", SharePayload.cleanNick(" a b "));
        assertEquals("ab", SharePayload.cleanName("a§\u0000b"));
    }

    @Test
    void outputNeverContainsForbiddenPatterns() {
        Random names = new Random(9);
        SecureRandom random = seeded(10);
        List<String> blocked = ShareCodec.parseBlocked("xxx, bl;  zz");
        assertEquals(List.of("xxx", "bl", "zz"), blocked);
        for (int i = 0; i < 2000; i++) {
            SharePayload original = new SharePayload(names.nextInt(), names.nextInt(), names.nextInt(700) - 64, names.nextInt(),
                    OVERWORLD, "Player" + i, "p" + names.nextInt(1_000_000));
            String token = ShareCodec.encode(original, key, "", blocked, random).token();
            String lower = token.toLowerCase(Locale.ROOT);
            assertFalse(token.contains("."), token);
            assertFalse(lower.contains("http"), token);
            assertFalse(lower.contains("www"), token);
            assertFalse(lower.contains("zz"), token);
            assertFalse(lower.contains("bl"), token);
            for (int c = 0; c < token.length(); c++) {
                char ch = token.charAt(c);
                assertTrue(ch > ' ' && ch < 127, token);
                if (c >= 2) {
                    assertFalse(ch == token.charAt(c - 1) && ch == token.charAt(c - 2), "run of 3 in " + token);
                }
            }
        }
    }

    @Test
    void safetyCheckRejectsKnownPatterns() {
        assertFalse(ShareCodec.isSafe("~cs1abhttpq", List.of()));
        assertFalse(ShareCodec.isSafe("~cs1abHTTPq", List.of()));
        assertFalse(ShareCodec.isSafe("~cs1a.b", List.of()));
        assertFalse(ShareCodec.isSafe("~cs1abbbc", List.of()));
        assertFalse(ShareCodec.isSafe("~cs1a b", List.of()));
        assertFalse(ShareCodec.isSafe("~cs1aяb", List.of()));
        assertFalse(ShareCodec.isSafe("~cs1abkkq", List.of("kk")));
        assertTrue(ShareCodec.isSafe("~cs1abbc", List.of()));
    }

    @Test
    void tokenIsFoundInsideFormattedChatLines() {
        ShareCodec.Encoded encoded = ShareCodec.encode(payload("Steve", "base"), key, "!", List.of(), seeded(11));
        String token = encoded.token();
        List<String> lines = List.of(
                "[G] [VIP] Steve » " + token,
                "<Steve> " + token,
                "ᴋʟᴀɴ ✦ [Admin] Steve: " + token + " (edited)",
                "[Clan] Steve:" + token + "!",
                "~cs broken first ~cs " + token + " tail",
                "Steve: " + token.toUpperCase(Locale.ROOT) + " ok");
        for (String line : lines) {
            List<ShareCodec.Token> found = ShareCodec.find(line);
            ShareCodec.Opened.Ok ok = null;
            for (ShareCodec.Token candidate : found) {
                if (ShareCodec.open(candidate, key) instanceof ShareCodec.Opened.Ok success) {
                    ok = success;
                    break;
                }
            }
            assertTrue(ok != null, "no valid token in " + line);
            assertEquals(encoded.sent(), ok.payload());
            assertEquals(line.toLowerCase(Locale.ROOT).indexOf(token.toLowerCase(Locale.ROOT)), ok.token().start());
            assertEquals(ok.token().start() + token.length(), ok.token().end());
        }
    }

    @Test
    void foreignAndUnsupportedMarkersAreRejected() {
        assertTrue(ShareCodec.find("hello world, no share here").isEmpty());
        assertEquals(ShareCodec.Failure.UNSUPPORTED_VERSION, ShareCodec.find("x ~cs2bc123").getFirst().failure());
        assertEquals(ShareCodec.Failure.TRUNCATED, ShareCodec.find("~cs").getFirst().failure());
        assertEquals(ShareCodec.Failure.BAD_LENGTH, ShareCodec.find("~cs100abc").getFirst().failure());
        assertEquals(ShareCodec.Failure.BAD_ENCODING, ShareCodec.find("~cs13babc_def").getFirst().failure());
    }

    @Test
    void trailingAlphabetCharactersAreIgnored() {
        String token = ShareCodec.encode(payload("Steve", "base"), key, "", List.of(), seeded(12)).token();
        ShareCodec.Opened.Ok ok = assertInstanceOf(ShareCodec.Opened.Ok.class, decodeFirst(token + "123", key));
        assertEquals(token.length(), ok.token().end());
    }

    @Test
    void staleSharesAreDetected() {
        SharePayload share = new SharePayload(1000, 0, 0, 0, OVERWORLD, "a", "");
        assertTrue(ShareCodec.isFresh(share, 1000 + 60, 120));
        assertFalse(ShareCodec.isFresh(share, 1000 + 121, 120));
        assertFalse(ShareCodec.isFresh(share, 1000 - 121, 120));
        assertTrue(ShareCodec.isFresh(share, 1_000_000, 0));
    }

    @Test
    void shareCommandIsParsed() {
        assertEquals("", ShareCodec.parseShareCommand(".share"));
        assertEquals("base camp", ShareCodec.parseShareCommand("  .share   base camp "));
        assertEquals("x", ShareCodec.parseShareCommand(".SHARE x"));
        assertNull(ShareCodec.parseShareCommand(".shared"));
        assertNull(ShareCodec.parseShareCommand("hi .share"));
        assertEquals("!!", ShareCodec.cleanPrefix("!§!\n"));
    }

    @Test
    void clickCommandArgumentsRoundTrip() {
        SharePayload original = new SharePayload(0, -5, 70, 12, "minecraft:the_nether", "Steve", "my  base / 2");
        SharePayload parsed = SharePayload.fromCommandArgs(original.toCommandArgs());
        assertEquals(original, parsed);
        assertEquals("", SharePayload.fromCommandArgs("add 1 2 3 minecraft:overworld Nick").name());
        assertNull(SharePayload.fromCommandArgs("add 1 2 x minecraft:overworld Nick"));
        assertNull(SharePayload.fromCommandArgs("add 1 2 3 overworld Nick"));
        assertNull(SharePayload.fromCommandArgs("remove all"));
    }

    @Test
    void base30RoundTripIncludingLeadingZeros() {
        Random random = new Random(13);
        for (int bytes = 1; bytes <= 160; bytes++) {
            byte[] data = new byte[bytes];
            random.nextBytes(data);
            if (bytes % 3 == 0) {
                data[0] = 0;
            }
            String text = Base30.encode(data);
            assertEquals(Base30.encodedLength(bytes), text.length());
            assertArrayEquals(data, Base30.decode(text, bytes));
            assertArrayEquals(data, Base30.decode(text.toUpperCase(Locale.ROOT), bytes));
        }
        assertNull(Base30.decode("zzzz", 2));
        assertNull(Base30.decode("zzz", 2));
    }
}
