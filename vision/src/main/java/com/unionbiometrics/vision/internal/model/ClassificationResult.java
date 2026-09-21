package com.unionbiometrics.vision.internal.model;

import androidx.annotation.RestrictTo;

import com.unionbiometrics.vision.api.ClassLabels;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class ClassificationResult {
    public static final String[] LABELS = ClassLabels.values();
    public final float[] probabilities;
    public final int topIndex;
    public final long preprocessMs;
    public final long inferenceMs;

    ClassificationResult(float[] probabilities, long preprocessMs, long inferenceMs) {
        if (probabilities == null || probabilities.length != LABELS.length) {
            throw new IllegalArgumentException(
                    "Probabilities must have length " + LABELS.length);
        }
        this.probabilities = probabilities;
        this.preprocessMs = preprocessMs;
        this.inferenceMs = inferenceMs;
        int best = 0;
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > probabilities[best]) best = i;
        }
        topIndex = best;
    }

}
