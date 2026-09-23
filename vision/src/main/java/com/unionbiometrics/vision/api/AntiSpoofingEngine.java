package com.unionbiometrics.vision.api;

/** Stable host-facing anti-spoofing session contract. */
public interface AntiSpoofingEngine extends AutoCloseable {
    /** Returns immutable metadata for this loaded model slot. */
    EngineInfo info();

    /**
     * Processes one borrowed RGB/IR frame pair, automatically starting a session when needed.
     * The SDK never recycles the supplied bitmaps.
     */
    AntiSpoofingResult process(AntiSpoofingFrame frame);

    /** Cancels the active session and clears accumulated probabilities. */
    void reset();

    @Override
    void close();
}
