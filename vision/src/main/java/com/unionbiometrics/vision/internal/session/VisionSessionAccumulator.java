package com.unionbiometrics.vision.internal.session;

import com.unionbiometrics.vision.api.AntiSpoofingResult;
import com.unionbiometrics.vision.internal.model.ClassificationResult;

final class VisionSessionAccumulator {
    private final int requiredSampleCount;
    private final float[] sums = new float[ClassificationResult.LABELS.length];
    private int sampleCount;

    VisionSessionAccumulator(int requiredSampleCount) {
        this.requiredSampleCount = requiredSampleCount;
    }

    AntiSpoofingResult add(float[] probabilities, long preprocessMs, long inferenceMs) {
        if (sampleCount >= requiredSampleCount) {
            throw new IllegalStateException("Vision session is already complete");
        }
        if (probabilities == null || probabilities.length != sums.length) {
            throw new IllegalArgumentException("Probabilities must have length " + sums.length);
        }
        for (int i = 0; i < sums.length; i++) sums[i] += probabilities[i];
        sampleCount++;

        float[] average = new float[sums.length];
        int topIndex = 0;
        for (int i = 0; i < sums.length; i++) {
            average[i] = sums[i] / sampleCount;
            if (average[i] > average[topIndex]) topIndex = i;
        }
        if (sampleCount < requiredSampleCount) {
            return AntiSpoofingResult.collecting(average, topIndex, sampleCount, requiredSampleCount,
                    preprocessMs, inferenceMs);
        }
        return AntiSpoofingResult.terminal(ClassificationResult.isAcceptedClass(topIndex),
                average, topIndex, sampleCount, preprocessMs, inferenceMs);
    }

    void reset() {
        java.util.Arrays.fill(sums, 0f);
        sampleCount = 0;
    }
}
