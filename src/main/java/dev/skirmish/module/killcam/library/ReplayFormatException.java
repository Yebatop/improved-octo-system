package dev.skirmish.module.killcam.library;

import java.io.IOException;

/** A replay file that cannot be read: not a replay, damaged, or written in an unsupported format version. */
public final class ReplayFormatException extends IOException {
    /** What is wrong with the file. */
    public enum Problem {
        /** Wrong magic: some other file in the folder. */
        NOT_A_REPLAY,
        /** Written by a newer Skirmish (format version above {@link ReplayCodec#VERSION}). */
        NEWER_VERSION,
        /** Format version below {@link ReplayCodec#MIN_VERSION}, no longer read. */
        OLD_VERSION,
        /** Truncated, checksum mismatch or inconsistent data. */
        CORRUPT
    }

    private final Problem problem;

    public ReplayFormatException(Problem problem, String message) {
        super(message);
        this.problem = problem;
    }

    public ReplayFormatException(String message) {
        this(Problem.CORRUPT, message);
    }

    public Problem problem() {
        return problem;
    }
}
