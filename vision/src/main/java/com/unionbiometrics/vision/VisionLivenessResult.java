package com.unionbiometrics.vision;

import java.util.Arrays;

/** One raw frame result or one callback session state. */
public final class VisionLivenessResult {
    private static final float MIN_SESSION_LIVE_SCORE = 0.4f;

    public enum Status { RUNNING, LIVE, SPOOF, ERROR }
    private final Status status;
    private final float[] probabilities;
    private final int topIndex;
    private final Long inferenceMs;
    private final String errorMessage;
    private final Long sessionDurationMs;

    static VisionLivenessResult frameResult(float[] probabilities, long inferenceMs) {
        if (probabilities == null) throw new IllegalArgumentException("Success requires a result");
        int topIndex = topIndexOf(probabilities);
        return new VisionLivenessResult(isLiveClass(topIndex) ? Status.LIVE : Status.SPOOF,
                probabilities, topIndex, inferenceMs, null, null);
    }

    static VisionLivenessResult frameError(String message) {
        return new VisionLivenessResult(Status.ERROR, null, -1, 0L,
                message == null ? "Unknown Vision inference error" : message, null);
    }

    static VisionLivenessResult sessionRunning(float[] probabilities, Long inferenceMs) {
        return new VisionLivenessResult(Status.RUNNING, probabilities,
                probabilities == null ? -1 : topIndexOf(probabilities),
                inferenceMs, null, null);
    }

    static VisionLivenessResult sessionComplete(float[] probabilities, long inferenceMs) {
        int topIndex = topIndexOf(probabilities);
        boolean accepted = isLiveClass(topIndex) && probabilities[topIndex] >= MIN_SESSION_LIVE_SCORE;
        return new VisionLivenessResult(accepted ? Status.LIVE : Status.SPOOF,
                probabilities, topIndex, inferenceMs, null, null);
    }

    static VisionLivenessResult sessionError(String message) {
        return new VisionLivenessResult(Status.ERROR, null, -1, null,
                message == null ? "Unknown Vision error" : message, null);
    }

    VisionLivenessResult(Status status, float[] probabilities, int topIndex,
                         Long inferenceMs, String errorMessage, Long sessionDurationMs) {
        if (sessionDurationMs != null && sessionDurationMs < 0) {
            throw new IllegalArgumentException("sessionDurationMs must be >= 0");
        }
        this.status = status;
        this.probabilities = probabilities == null ? null : probabilities.clone();
        this.topIndex = topIndex;
        this.inferenceMs = inferenceMs;
        this.errorMessage = errorMessage;
        this.sessionDurationMs = sessionDurationMs;
    }

    public Status getStatus() { return status; }
    /** Returns a copy in VisionModelInfo.classLabels order, or null on error/settling. */
    public float[] getProbabilities() { return probabilities == null ? null : probabilities.clone(); }
    public int getTopIndex() { return topIndex; }
    public float getScore() { return topIndex < 0 ? 0f : probabilities[topIndex]; }
    /** Model invocation time for a raw frame; the last sampled frame's time for a session. */
    public Long getInferenceMs() { return inferenceMs; }
    public String getErrorMessage() { return errorMessage; }
    /** First accepted inferSession call through callback delivery; null for raw frames. */
    public Long getSessionDurationMs() { return sessionDurationMs; }

    static int topIndexOf(float[] probabilities) {
        if (probabilities == null || probabilities.length != Constants.CLASS_COUNT) {
            throw new IllegalArgumentException("probabilities must have length " + Constants.CLASS_COUNT);
        }
        int best = 0;
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > probabilities[best]) best = i;
        }
        return best;
    }

    static boolean isLiveClass(int index) {
        return index == Arrays.asList(Constants.CLASS_LABELS).indexOf("LIVE")
                || index == Arrays.asList(Constants.CLASS_LABELS).indexOf("DENTAL_WHITE")
                || index == Arrays.asList(Constants.CLASS_LABELS).indexOf("DENTAL_BLACK");
    }
}
