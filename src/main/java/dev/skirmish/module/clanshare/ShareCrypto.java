package dev.skirmish.module.clanshare;

import org.jspecify.annotations.Nullable;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Clan key and message sealing, protocol version 1. No Minecraft types.
 * <ul>
 *     <li>key: PBKDF2-HMAC-SHA256(password, {@link #SALT}, {@link #ITERATIONS}) → AES-256;</li>
 *     <li>message: AES-GCM with a fresh random 12-byte nonce and a 128-bit tag, AAD = {@link #AAD};
 *     sealed form = nonce ‖ ciphertext ‖ tag.</li>
 * </ul>
 */
final class ShareCrypto {
    static final int ITERATIONS = 150_000;
    static final int KEY_BITS = 256;
    static final int NONCE_BYTES = 12;
    static final int TAG_BYTES = 16;
    static final int OVERHEAD = NONCE_BYTES + TAG_BYTES;

    // The salt is a protocol constant (every clan member must derive the same key); changing it breaks compatibility.
    private static final byte[] SALT = "skirmish:clanshare:salt:v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AAD = "skirmish:clanshare:v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FINGERPRINT_LABEL = "skirmish:clanshare:fingerprint:v1".getBytes(StandardCharsets.US_ASCII);
    private static final String CIPHER = "AES/GCM/NoPadding";

    private ShareCrypto() {
    }

    /** Same password → same key on every client: NFC-normalized, surrounding whitespace ignored. */
    static String normalizePassword(String password) {
        return Normalizer.normalize(password.strip(), Normalizer.Form.NFC);
    }

    /** Slow on purpose (~0.1–0.3 s); call off the render thread. */
    static SecretKey deriveKey(String password) {
        String normalized = normalizePassword(password);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Empty password");
        }
        char[] chars = normalized.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, SALT, ITERATIONS, KEY_BITS);
        try {
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            SecretKey key = new SecretKeySpec(encoded, "AES");
            Arrays.fill(encoded, (byte) 0);
            return key;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }

    /** nonce ‖ ciphertext ‖ tag. */
    static byte[] seal(SecretKey key, byte[] plaintext, SecureRandom random) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BYTES * 8, nonce));
            cipher.updateAAD(AAD);
            byte[] sealed = new byte[NONCE_BYTES + cipher.getOutputSize(plaintext.length)];
            System.arraycopy(nonce, 0, sealed, 0, NONCE_BYTES);
            cipher.doFinal(plaintext, 0, plaintext.length, sealed, NONCE_BYTES);
            return sealed;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM unavailable", e);
        }
    }

    /** The plaintext, or null when the tag does not verify (wrong password or modified message). */
    static byte @Nullable [] open(SecretKey key, byte[] sealed) {
        if (sealed.length < OVERHEAD) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BYTES * 8, sealed, 0, NONCE_BYTES));
            cipher.updateAAD(AAD);
            return cipher.doFinal(sealed, NONCE_BYTES, sealed.length - NONCE_BYTES);
        } catch (AEADBadTagException e) {
            return null;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM unavailable", e);
        }
    }

    /**
     * 8 hex chars that clan mates can compare to check they typed the same password. One-way (HMAC of the key),
     * and gives an attacker nothing beyond what any encrypted chat message already allows (offline guessing).
     */
    static String fingerprint(SecretKey key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(FINGERPRINT_LABEL), 0, 4);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
