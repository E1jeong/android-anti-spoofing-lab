package com.unionbiometrics.vision;

import java.util.Arrays;

/** One raw frame result or one callback session state. */
public final class AntiSpoofingResult {
    public enum Status { RUNNING, LIVE, SPOOF, ERROR }
    private final Status status;
    private final float[] probabilities;
    private final int topIndex;
    private final Long inferenceMs;
    private final long preprocessMs;
    private final String errorMessage;
    private final int settleFramesRemaining;
    private final int usedFrameCount;

    static AntiSpoofingResult frameResult(float[] probabilities, long preprocessMs, long inferenceMs) {
        if (probabilities == null) throw new IllegalArgumentException("Success requires a result");
        int topIndex = topIndexOf(probabilities);
        return new AntiSpoofingResult(isLiveClass(topIndex) ? Status.LIVE : Status.SPOOF,
                probabilities, topIndex, inferenceMs, preprocessMs, null, 0, 0);
    }

    static AntiSpoofingResult frameError(String message) {
        return new AntiSpoofingResult(Status.ERROR, null, -1, 0L, 0L,
                message == null ? "Unknown Vision inference error" : message, 0, 0);
    }

    static AntiSpoofingResult sessionRunning(float[] probabilities, Long inferenceMs,
                                            int settleFramesRemaining, int usedFrameCount) {
        return new AntiSpoofingResult(Status.RUNNING, probabilities,
                probabilities == null ? -1 : topIndexOf(probabilities),
                inferenceMs, 0L, null, settleFramesRemaining, usedFrameCount);
    }

    static AntiSpoofingResult sessionComplete(float[] probabilities, long inferenceMs,
                                             int settleFramesRemaining, int usedFrameCount) {
        int topIndex = topIndexOf(probabilities);
        return new AntiSpoofingResult(isLiveClass(topIndex) ? Status.LIVE : Status.SPOOF,
                probabilities, topIndex, inferenceMs, 0L, null, settleFramesRemaining, usedFrameCount);
    }

    static AntiSpoofingResult sessionError(String message) {
        return new AntiSpoofingResult(Status.ERROR, null, -1, null, 0L,
                message == null ? "Unknown Vision error" : message, 0, 0);
    }

    private AntiSpoofingResult(Status status, float[] probabilities, int topIndex,
                              Long inferenceMs, long preprocessMs, String errorMessage,
                              int settleFramesRemaining, int usedFrameCount) {
        this.status = status;
        this.probabilities = probabilities == null ? null : probabilities.clone();
        this.topIndex = topIndex;
        this.inferenceMs = inferenceMs;
        this.preprocessMs = preprocessMs;
        this.errorMessage = errorMessage;
        this.settleFramesRemaining = settleFramesRemaining;
        this.usedFrameCount = usedFrameCount;
    }

    public Status status() { return status; }
    public float[] probabilities() { return probabilities == null ? null : probabilities.clone(); }
    public int topIndex() { return topIndex; }
    public float score() { return topIndex < 0 ? 0f : probabilities[topIndex]; }
    public float probability(int index) { return probabilities[index]; }
    public boolean isAccepted() { return status == Status.LIVE; }
    public static String[] classLabels() { return Constants.CLASS_LABELS.clone(); }
    public static String classDisplayLabel(int index) {
        String label = Constants.CLASS_LABELS[index];
        return label.startsWith("CURVED_")
                ? "C " + label.substring("CURVED_".length()) : label;
    }
    public String displayLabel() { return topIndex < 0 ? null : classDisplayLabel(topIndex); }
    public Long inferenceMs() { return inferenceMs; }
    public long preprocessMs() { return preprocessMs; }
    public String errorMessage() { return errorMessage; }
    public int settleFramesRemaining() { return settleFramesRemaining; }
    public int usedFrameCount() { return usedFrameCount; }

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
