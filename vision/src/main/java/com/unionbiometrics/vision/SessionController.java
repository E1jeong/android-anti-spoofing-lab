package com.unionbiometrics.vision;

import java.util.Arrays;

final class SessionController {
    private final int irSettleFrameCount;
    private final int requiredSamplingCount;
    private final float[] sums = new float[VisionConstants.CLASS_COUNT];
    private int samplingCount;
    private boolean sessionActive;
    private boolean closed;
    private int settleFramesRemaining;
    private AntiSpoofingResult decision;

    SessionController(int irSettleFrameCount, int samplingCount) {
        if (irSettleFrameCount < 0) {
            throw new IllegalArgumentException("irSettleFrameCount must be >= 0");
        }
        if (samplingCount <= 0) throw new IllegalArgumentException("sampleCount must be > 0");
        this.irSettleFrameCount = irSettleFrameCount;
        this.requiredSamplingCount = samplingCount;
    }

    private void start() {
        clearState();
        sessionActive = true;
        settleFramesRemaining = irSettleFrameCount;
    }

    /** Returns null only when the caller may run inference and add a sample. */
    AntiSpoofingResult prepare() {
        if (closed) return AntiSpoofingResult.sessionError("Vision engine is closed");
        if (!sessionActive) start();
        if (decision != null) return decision;
        if (settleFramesRemaining > 0) {
            settleFramesRemaining--;
            return AntiSpoofingResult.sessionRunning(null, null, settleFramesRemaining, 0);
        }
        return null;
    }

    AntiSpoofingResult add(float[] classification, long inferenceMs) {
        if (classification == null) return fail("Vision slot produced no primary result");
        if (samplingCount >= requiredSamplingCount) {
            throw new IllegalStateException("Vision session is already complete");
        }
        if (classification.length != sums.length) {
            throw new IllegalArgumentException("Probabilities must have length " + sums.length);
        }
        for (int i = 0; i < sums.length; i++) sums[i] += classification[i];
        samplingCount++;
        float[] average = new float[sums.length];
        for (int i = 0; i < sums.length; i++) average[i] = sums[i] / samplingCount;
        AntiSpoofingResult result = samplingCount < requiredSamplingCount
                ? AntiSpoofingResult.sessionRunning(average, inferenceMs,
                        settleFramesRemaining, samplingCount)
                : AntiSpoofingResult.sessionDecision(average, inferenceMs,
                        settleFramesRemaining, samplingCount);
        if (result.status() == AntiSpoofingResult.Status.LIVE
                || result.status() == AntiSpoofingResult.Status.SPOOF) {
            decision = result;
        }
        return result;
    }

    AntiSpoofingResult fail(String message) {
        clearState();
        return AntiSpoofingResult.sessionError(message);
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

    private void clearState() {
        sessionActive = false;
        settleFramesRemaining = 0;
        decision = null;
        Arrays.fill(sums, 0f);
        samplingCount = 0;
    }

}
