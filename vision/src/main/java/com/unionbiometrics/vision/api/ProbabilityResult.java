package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

import java.util.Objects;

/** Immutable probabilities for one model output. */
public final class ProbabilityResult {
    private static final int CLASS_COUNT = ClassLabels.count();
    private final float[] probabilities;
    private final int topIndex;

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public ProbabilityResult(float[] probabilities) {
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

    /** Returns whether the winning class belongs to the product bona-fide pass set. */
    public boolean isAccepted() {
        return ClassLabels.isAcceptedClass(topIndex);
    }
}
