package dev.skirmish.module.clanshare;

import dev.skirmish.module.Module;
import org.jspecify.annotations.Nullable;

import javax.crypto.SecretKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** The clan key, derived on a background thread whenever the password changes. Reads are lock-free. */
final class ClanKey {
    enum State { NO_PASSWORD, DERIVING, READY, FAILED }

    private record Derived(int generation, @Nullable SecretKey key, @Nullable String fingerprint, State state) {
    }

    private final Module owner;
    private final AtomicInteger generation = new AtomicInteger();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Skirmish ClanShare key");
        t.setDaemon(true);
        return t;
    });
    private volatile Derived current = new Derived(0, null, null, State.NO_PASSWORD);

    ClanKey(Module owner) {
        this.owner = owner;
    }

    /** Starts a new derivation; a result for an older password is discarded. */
    void setPassword(String password) {
        int gen = generation.incrementAndGet();
        if (ShareCrypto.normalizePassword(password).isEmpty()) {
            current = new Derived(gen, null, null, State.NO_PASSWORD);
            owner.log("password cleared, sharing and decoding are off");
            return;
        }
        current = new Derived(gen, null, null, State.DERIVING);
        owner.log("password changed, deriving key: PBKDF2-HMAC-SHA256, " + ShareCrypto.ITERATIONS + " iterations");
        executor.execute(() -> {
            if (generation.get() != gen) {
                owner.log("key derivation #" + gen + " skipped, password changed again");
                return;
            }
            long start = System.nanoTime();
            try {
                SecretKey key = ShareCrypto.deriveKey(password);
                String fingerprint = ShareCrypto.fingerprint(key);
                long ms = (System.nanoTime() - start) / 1_000_000;
                if (generation.get() == gen) {
                    current = new Derived(gen, key, fingerprint, State.READY);
                    owner.log("key #" + gen + " ready in " + ms + " ms, fingerprint " + fingerprint);
                } else {
                    owner.log("key #" + gen + " derived in " + ms + " ms but discarded, password changed meanwhile");
                }
            } catch (RuntimeException e) {
                if (generation.get() == gen) {
                    current = new Derived(gen, null, null, State.FAILED);
                }
                owner.error("key derivation failed", e);
            }
        });
    }

    State state() {
        return current.state();
    }

    @Nullable SecretKey key() {
        return current.key();
    }

    @Nullable String fingerprint() {
        return current.fingerprint();
    }
}
