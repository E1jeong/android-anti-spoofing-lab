package com.unionbiometrics.vision;

import com.unionbiometrics.vision.internal.ClassLabels;
import com.unionbiometrics.vision.internal.ProbabilityResult;

/** One session update. A decision is final until reset(). */
public final class AntiSpoofingResult {
    public enum Status { PENDING, LIVE, SPOOF, ERROR }
    public enum Attack {
        NONE, PRINT, PICTURE, MASK, DISPLAY, PMASK,
        CURVED_PRINT, CURVED_MASK, CURVED_PICTURE, CURVED_PMASK
    }

    private final Status status;
    private final float[] probabilities;
    private final int topIndex;
    private final Long inferenceMs;
    private final String errorMessage;
    private final int settleRemaining;
    private final int acceptedSamples;

    static AntiSpoofingResult fromInternal(
            com.unionbiometrics.vision.internal.SessionResult source,
            int settleRemaining, int acceptedSamples) {
        ProbabilityResult probability = source.result();
        return new AntiSpoofingResult(Status.valueOf(source.status().name()),
                probability == null ? null : probability.probabilities(),
                probability == null ? -1 : probability.topIndex(),
                source.inferenceMs(), source.errorMessage(), settleRemaining, acceptedSamples);
    }

    private AntiSpoofingResult(Status status, float[] probabilities, int topIndex,
                              Long inferenceMs, String errorMessage,
                              int settleRemaining, int acceptedSamples) {
        this.status = status;
        this.probabilities = probabilities == null ? null : probabilities.clone();
        this.topIndex = topIndex;
        this.inferenceMs = inferenceMs;
        this.errorMessage = errorMessage;
        this.settleRemaining = settleRemaining;
        this.acceptedSamples = acceptedSamples;
    }

    public Status status() { return status; }
    public float[] probabilities() { return probabilities == null ? null : probabilities.clone(); }
    public int topIndex() { return topIndex; }
    public float score() { return topIndex < 0 ? 0f : probabilities[topIndex]; }
    public Attack attack() {
        return topIndex < 0 || ClassLabels.isAcceptedClass(topIndex)
                ? Attack.NONE : Attack.valueOf(ClassLabels.values()[topIndex]);
    }
    public String displayLabel() { return topIndex < 0 ? null : ClassLabels.displayLabel(topIndex); }
    public Long inferenceMs() { return inferenceMs; }
    public String errorMessage() { return errorMessage; }
    public int settleRemaining() { return settleRemaining; }
    public int acceptedSamples() { return acceptedSamples; }
}
