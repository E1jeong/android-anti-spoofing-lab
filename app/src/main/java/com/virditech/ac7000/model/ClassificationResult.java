package com.virditech.ac7000.model;

public final class ClassificationResult {
    public static final String[] LABELS = {"LIVE", "SPOOF_MASK", "DISPLAY", "PHOTO"};
    public final float[] probabilities;
    public final int topIndex;
    public final long inferenceMs;

    ClassificationResult(float[] probabilities, long inferenceMs) {
        this.probabilities = probabilities;
        this.inferenceMs = inferenceMs;
        int best = 0;
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > probabilities[best]) best = i;
        }
        topIndex = best;
    }
}
