package com.unionbiometrics.vision;

final class SessionAccumulator {
    private final int requiredSampleCount;
    private final float[] sums = new float[ClassLabels.count()];
    private int sampleCount;

    SessionAccumulator(int requiredSampleCount) {
        this.requiredSampleCount = requiredSampleCount;
    }

    SessionResult add(float[] probabilities, long inferenceMs) {
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
        if (sampleCount < requiredSampleCount) return SessionResult.pending(result, inferenceMs);
        return SessionResult.decision(result, inferenceMs);
    }

    void reset() {
        java.util.Arrays.fill(sums, 0f);
        sampleCount = 0;
    }

    int sampleCount() {
        return sampleCount;
    }
}
