package com.unionbiometrics.vision.internal.session;

import com.unionbiometrics.vision.api.AntiSpoofingResult;
import com.unionbiometrics.vision.api.ProbabilityResult;
import com.unionbiometrics.vision.internal.model.ClassificationResult;

final class SessionAccumulator {
    private final int requiredSampleCount;
    private final float[] sums = new float[ClassificationResult.LABELS.length];
    private int sampleCount;

    SessionAccumulator(int requiredSampleCount) {
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
        for (int i = 0; i < sums.length; i++) {
            average[i] = sums[i] / sampleCount;
        }
        ProbabilityResult result = new ProbabilityResult(average);
        if (sampleCount < requiredSampleCount) {
            return AntiSpoofingResult.collecting(result, sampleCount, requiredSampleCount,
                    preprocessMs, inferenceMs);
        }
        return AntiSpoofingResult.terminal(result, sampleCount, preprocessMs, inferenceMs);
    }

    void reset() {
        java.util.Arrays.fill(sums, 0f);
        sampleCount = 0;
    }
}
