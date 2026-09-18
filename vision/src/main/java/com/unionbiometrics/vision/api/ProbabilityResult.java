package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

import java.util.Objects;

/** Immutable probabilities and timing for one model output. */
public final class ProbabilityResult {
    private static final int CLASS_COUNT = ClassLabels.count();
    private final float[] probabilities;
    private final int topIndex;
    private final long preprocessMs;
    private final long inferenceMs;

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public ProbabilityResult(float[] probabilities, long preprocessMs, long inferenceMs) {
        Objects.requireNonNull(probabilities, "probabilities");
        if (probabilities.length != CLASS_COUNT) {
            throw new IllegalArgumentException(
                    "probabilities must have length " + CLASS_COUNT);
        }
        this.probabilities = probabilities.clone();
        int best = 0;
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > probabilities[best]) best = i;
        }
        this.topIndex = best;
        this.preprocessMs = preprocessMs;
        this.inferenceMs = inferenceMs;
    }

    public float[] probabilities() {
        return probabilities.clone();
    }

    public float probability(int index) {
        return probabilities[index];
    }

    public int topIndex() {
        return topIndex;
    }

    public String topLabel() {
        return ClassLabels.label(topIndex);
    }

    public String topDisplayLabel() {
        return ClassLabels.displayLabel(topIndex);
    }

    public boolean isLive() {
        return ClassLabels.isAcceptedClass(topIndex);
    }

    public long preprocessMs() {
        return preprocessMs;
    }

    public long inferenceMs() {
        return inferenceMs;
    }
}
