package com.unionbiometrics.vision.internal.model;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class ClassificationResult {
    public static final String[] LABELS = {
            "LIVE", "PRINT", "PICTURE", "MASK", "DISPLAY", "PMASK",
            "CURVED_PRINT", "CURVED_MASK", "CURVED_PICTURE", "CURVED_PMASK",
            "DENTAL_WHITE", "DENTAL_BLACK"
    };
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

    public static String displayLabel(int index) {
        String label = LABELS[index];
        return label.startsWith("CURVED_") ? "C " + label.substring("CURVED_".length()) : label;
    }

    public static boolean isAcceptedClass(int index) {
        return index == 0 || index == 10 || index == 11;
    }
}
