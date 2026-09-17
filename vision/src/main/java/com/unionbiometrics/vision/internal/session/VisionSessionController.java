package com.unionbiometrics.vision.internal.session;

import com.unionbiometrics.vision.api.VisionClassification;
import com.unionbiometrics.vision.api.VisionOptions;
import com.unionbiometrics.vision.api.VisionResult;

final class VisionSessionController {
    interface IlluminationControl {
        void setEnabled(boolean enabled);
    }

    interface Clock {
        long elapsedRealtimeMs();
    }

    private final VisionOptions options;
    private final IlluminationControl illumination;
    private final Clock clock;
    private final VisionSessionAccumulator accumulator;
    private boolean sessionActive;
    private boolean closed;
    private long settleStartedMs;
    private VisionResult terminalResult;

    VisionSessionController(VisionOptions options, IlluminationControl illumination, Clock clock) {
        this.options = options;
        this.illumination = illumination;
        this.clock = clock;
        accumulator = new VisionSessionAccumulator(options.sampleCount());
    }

    VisionResult start() {
        if (closed) return error("Vision engine is closed");
        clearState();
        try {
            illumination.setEnabled(true);
        } catch (RuntimeException e) {
            requestIlluminationOff();
            return error("IR illumination failed: " + e.getMessage());
        }
        sessionActive = true;
        settleStartedMs = clock.elapsedRealtimeMs();
        return VisionResult.settling(options.sampleCount(), options.irSettleMs());
    }

    /** Returns null only when the caller may classify and add a sample. */
    VisionResult beforeSample() {
        if (closed) return error("Vision engine is closed");
        if (!sessionActive) return start();
        if (terminalResult != null) return terminalResult;
        long elapsedMs = clock.elapsedRealtimeMs() - settleStartedMs;
        if (elapsedMs < options.irSettleMs()) {
            return VisionResult.settling(options.sampleCount(), options.irSettleMs() - elapsedMs);
        }
        return null;
    }

    VisionResult add(VisionClassification classification) {
        if (classification == null) return fail("Vision slot produced no primary result");
        VisionResult result = accumulator.add(classification.probabilities(),
                classification.preprocessMs(), classification.inferenceMs());
        if (result.status() == VisionResult.Status.LIVE
                || result.status() == VisionResult.Status.SPOOF) {
            terminalResult = result;
        }
        return result;
    }

    VisionResult fail(String message) {
        clearState();
        requestIlluminationOff();
        return error(message);
    }

    void reset() {
        if (closed) return;
        clearState();
        requestIlluminationOff();
    }

    void close() {
        if (closed) return;
        clearState();
        requestIlluminationOff();
        closed = true;
    }

    boolean isClosed() {
        return closed;
    }

    private VisionResult error(String message) {
        return VisionResult.error(options.sampleCount(), message);
    }

    private void clearState() {
        sessionActive = false;
        settleStartedMs = 0L;
        terminalResult = null;
        accumulator.reset();
    }

    private void requestIlluminationOff() {
        try {
            illumination.setEnabled(false);
        } catch (RuntimeException ignored) {
            // Cleanup remains best effort; state must still be cleared and close must complete.
        }
    }
}
