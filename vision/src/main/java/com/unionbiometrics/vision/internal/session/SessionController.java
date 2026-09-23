package com.unionbiometrics.vision.internal.session;

import androidx.annotation.RestrictTo;

import com.unionbiometrics.vision.api.ProbabilityResult;
import com.unionbiometrics.vision.api.AntiSpoofingOptions;
import com.unionbiometrics.vision.api.AntiSpoofingResult;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class SessionController {
    private final AntiSpoofingOptions options;
    private final SessionAccumulator accumulator;
    private boolean sessionActive;
    private boolean closed;
    private int settleFramesRemaining;
    private AntiSpoofingResult decision;

    public SessionController(AntiSpoofingOptions options) {
        this.options = options;
        accumulator = new SessionAccumulator(options.sampleCount());
    }

    private void start() {
        clearState();
        sessionActive = true;
        settleFramesRemaining = options.irSettleFrameCount();
    }

    /** Returns null only when the caller may classify and add a sample. */
    public AntiSpoofingResult beforeSample() {
        if (closed) return error("Vision engine is closed");
        if (!sessionActive) start();
        if (decision != null) return decision;
        if (settleFramesRemaining > 0) {
            settleFramesRemaining--;
            return AntiSpoofingResult.pending(null);
        }
        return null;
    }

    public AntiSpoofingResult add(ProbabilityResult classification, long inferenceMs) {
        if (classification == null) return fail("Vision slot produced no primary result");
        AntiSpoofingResult result = accumulator.add(classification.probabilities(), inferenceMs);
        if (result.status() == AntiSpoofingResult.Status.LIVE
                || result.status() == AntiSpoofingResult.Status.SPOOF) {
            decision = result;
        }
        return result;
    }

    public AntiSpoofingResult fail(String message) {
        clearState();
        return error(message);
    }

    public void reset() {
        if (closed) return;
        clearState();
    }

    public void close() {
        if (closed) return;
        clearState();
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    private AntiSpoofingResult error(String message) {
        return AntiSpoofingResult.error(message);
    }

    private void clearState() {
        sessionActive = false;
        settleFramesRemaining = 0;
        decision = null;
        accumulator.reset();
    }

}
