package com.unionbiometrics.vision;

import java.util.Arrays;

final class SessionController {
    private final int settleFrames;
    private final int sampleCount;
    private final float[] probabilitySums = new float[Constants.CLASS_COUNT];
    private int usedFrameCount;
    private boolean sessionActive;
    private boolean closed;
    private int settleFramesRemaining;
    private VisionLivenessResult finalResult;

    SessionController(int settleFrames, int sampleCount) {
        if (settleFrames < 0) {
            throw new IllegalArgumentException("settleFrames must be >= 0");
        }
        if (sampleCount <= 0) throw new IllegalArgumentException("sampleCount must be > 0");
        this.settleFrames = settleFrames;
        this.sampleCount = sampleCount;
    }

    private void start() {
        clearState();
        sessionActive = true;
        settleFramesRemaining = settleFrames;
    }

    /** Returns null only when the caller may run inference and add a sample. */
    VisionLivenessResult prepare() {
        if (closed) return VisionLivenessResult.sessionError("Vision engine is closed");
        if (!sessionActive) start();
        if (finalResult != null) return finalResult;
        if (settleFramesRemaining > 0) {
            settleFramesRemaining--;
            return VisionLivenessResult.sessionRunning(null, null);
        }
        return null;
    }

    VisionLivenessResult add(float[] probabilities, long inferenceMs) {
        if (probabilities == null) return fail("Vision slot produced no primary result");
        if (usedFrameCount >= sampleCount) {
            throw new IllegalStateException("Vision session is already complete");
        }
        if (probabilities.length != probabilitySums.length) {
            throw new IllegalArgumentException("Probabilities must have length " + probabilitySums.length);
        }
        for (int i = 0; i < probabilitySums.length; i++) probabilitySums[i] += probabilities[i];
        usedFrameCount++;
        float[] averageProbabilities = new float[probabilitySums.length];
        for (int i = 0; i < probabilitySums.length; i++) averageProbabilities[i] = probabilitySums[i] / usedFrameCount;
        VisionLivenessResult result = usedFrameCount < sampleCount
                ? VisionLivenessResult.sessionRunning(averageProbabilities, inferenceMs)
                : VisionLivenessResult.sessionComplete(averageProbabilities, inferenceMs);
        if (result.getStatus() == VisionLivenessResult.Status.LIVE
                || result.getStatus() == VisionLivenessResult.Status.SPOOF) {
            finalResult = result;
        }
        return result;
    }

    VisionLivenessResult fail(String message) {
        clearState();
        return VisionLivenessResult.sessionError(message);
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
        finalResult = null;
        Arrays.fill(probabilitySums, 0f);
        usedFrameCount = 0;
    }

}
