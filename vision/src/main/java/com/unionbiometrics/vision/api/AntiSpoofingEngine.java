package com.unionbiometrics.vision.api;

import android.graphics.Rect;

/** Stable host-facing anti-spoofing session contract. */
public interface AntiSpoofingEngine extends AutoCloseable {
    String label();

    String backendStatus();

    float cropMarginRatio();

    /** Expands a camera-coordinate face box using this model slot's crop contract. */
    Rect expandFaceBox(Rect faceBox, int imageWidth, int imageHeight);

    /** Runs one raw diagnostic inference without the product settle/averaging session. */
    VisionInferenceResult infer(VisionFrame frame);

    /** Starts a new IR-settle and multi-frame decision session. */
    VisionResult startSession();

    /** Processes one borrowed RGB/IR frame pair. The SDK never recycles the supplied bitmaps. */
    VisionResult process(VisionFrame frame);

    /** Cancels the active session, clears accumulated probabilities, and requests IR off. */
    void reset();

    @Override
    void close();
}
