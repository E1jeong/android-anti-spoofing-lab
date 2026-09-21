package com.unionbiometrics.vision.api;

/** Stable host-facing anti-spoofing session contract. */
public interface AntiSpoofingEngine extends AutoCloseable {
    /** Returns immutable metadata for this loaded model slot. */
    EngineInfo info();

    /** Runs one raw diagnostic inference without the product settle/averaging session. */
    InferenceResult infer(AntiSpoofingFrame frame);

    /** Starts a new IR-settle and multi-frame decision session. */
    AntiSpoofingResult startSession();

    /** Processes one borrowed RGB/IR frame pair. The SDK never recycles the supplied bitmaps. */
    AntiSpoofingResult process(AntiSpoofingFrame frame);

    /** Cancels the active session, clears accumulated probabilities, and requests IR off. */
    void reset();

    @Override
    void close();
}
