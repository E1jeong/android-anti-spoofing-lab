package com.unionbiometrics.vision;

final class SessionController {
    private final int irSettleFrameCount;
    private final SessionAccumulator accumulator;
    private boolean sessionActive;
    private boolean closed;
    private int settleFramesRemaining;
    private SessionResult decision;

    SessionController(int irSettleFrameCount, int sampleCount) {
        if (irSettleFrameCount < 0) {
            throw new IllegalArgumentException("irSettleFrameCount must be >= 0");
        }
        if (sampleCount <= 0) throw new IllegalArgumentException("sampleCount must be > 0");
        this.irSettleFrameCount = irSettleFrameCount;
        accumulator = new SessionAccumulator(sampleCount);
    }

    private void start() {
        clearState();
        sessionActive = true;
        settleFramesRemaining = irSettleFrameCount;
    }

    /** Returns null only when the caller may classify and add a sample. */
    SessionResult beforeSample() {
        if (closed) return error("Vision engine is closed");
        if (!sessionActive) start();
        if (decision != null) return decision;
        if (settleFramesRemaining > 0) {
            settleFramesRemaining--;
            return SessionResult.pending(null);
        }
        return null;
    }

    SessionResult add(ProbabilityResult classification, long inferenceMs) {
        if (classification == null) return fail("Vision slot produced no primary result");
        SessionResult result = accumulator.add(classification.probabilities(), inferenceMs);
        if (result.status() == SessionResult.Status.LIVE
                || result.status() == SessionResult.Status.SPOOF) {
            decision = result;
        }
        return result;
    }

    SessionResult fail(String message) {
        clearState();
        return error(message);
    }

    void reset() {
        if (closed) return;
        clearState();
    }

    void close() {
        if (closed) return;
        clearState();
        closed = true;
    }

    int settleRemaining() {
        return settleFramesRemaining;
    }

    int acceptedSamples() {
        return accumulator.sampleCount();
    }

    private SessionResult error(String message) {
        return SessionResult.error(message);
    }

    private void clearState() {
        sessionActive = false;
        settleFramesRemaining = 0;
        decision = null;
        accumulator.reset();
    }

}
