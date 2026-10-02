package com.unionbiometrics.vision;

/** One raw frame result or one callback session state. */
public final class AntiSpoofingResult {
    public enum Status { RUNNING, LIVE, SPOOF, ERROR }
    private final Status status;
    private final float[] probabilities;
    private final int topIndex;
    private final Long inferenceMs;
    private final long preprocessMs;
    private final String errorMessage;
    private final int settleRemaining;
    private final int acceptedSamples;

    static AntiSpoofingResult frame(float[] probabilities, long preprocessMs, long inferenceMs) {
        if (probabilities == null) throw new IllegalArgumentException("Success requires a result");
        int topIndex = topIndexOf(probabilities);
        return new AntiSpoofingResult(isAcceptedClass(topIndex) ? Status.LIVE : Status.SPOOF,
                probabilities, topIndex, inferenceMs, preprocessMs, null, 0, 0);
    }

    static AntiSpoofingResult frameError(String message) {
        return new AntiSpoofingResult(Status.ERROR, null, -1, 0L, 0L,
                message == null ? "Unknown Vision inference error" : message, 0, 0);
    }

    static AntiSpoofingResult sessionRunning(float[] probabilities, Long inferenceMs,
                                            int settleRemaining, int acceptedSamples) {
        return new AntiSpoofingResult(Status.RUNNING, probabilities,
                probabilities == null ? -1 : topIndexOf(probabilities),
                inferenceMs, 0L, null, settleRemaining, acceptedSamples);
    }

    static AntiSpoofingResult sessionDecision(float[] probabilities, long inferenceMs,
                                             int settleRemaining, int acceptedSamples) {
        int topIndex = topIndexOf(probabilities);
        return new AntiSpoofingResult(isAcceptedClass(topIndex) ? Status.LIVE : Status.SPOOF,
                probabilities, topIndex, inferenceMs, 0L, null, settleRemaining, acceptedSamples);
    }

    static AntiSpoofingResult sessionError(String message) {
        return new AntiSpoofingResult(Status.ERROR, null, -1, null, 0L,
                message == null ? "Unknown Vision error" : message, 0, 0);
    }

    private AntiSpoofingResult(Status status, float[] probabilities, int topIndex,
                              Long inferenceMs, long preprocessMs, String errorMessage,
                              int settleRemaining, int acceptedSamples) {
        this.status = status;
        this.probabilities = probabilities == null ? null : probabilities.clone();
        this.topIndex = topIndex;
        this.inferenceMs = inferenceMs;
        this.preprocessMs = preprocessMs;
        this.errorMessage = errorMessage;
        this.settleRemaining = settleRemaining;
        this.acceptedSamples = acceptedSamples;
    }

    public Status status() { return status; }
    public float[] probabilities() { return probabilities == null ? null : probabilities.clone(); }
    public int topIndex() { return topIndex; }
    public float score() { return topIndex < 0 ? 0f : probabilities[topIndex]; }
    public float probability(int index) { return probabilities[index]; }
    public boolean isAccepted() { return status == Status.LIVE; }
    public static String[] classLabels() { return VisionConstants.CLASS_LABELS.clone(); }
    public static String classDisplayLabel(int index) {
        String label = VisionConstants.CLASS_LABELS[index];
        return label.startsWith("CURVED_")
                ? "C " + label.substring("CURVED_".length()) : label;
    }
    public String displayLabel() { return topIndex < 0 ? null : classDisplayLabel(topIndex); }
    public Long inferenceMs() { return inferenceMs; }
    public long preprocessMs() { return preprocessMs; }
    public String errorMessage() { return errorMessage; }
    public int settleRemaining() { return settleRemaining; }
    public int acceptedSamples() { return acceptedSamples; }

    static int topIndexOf(float[] probabilities) {
        if (probabilities == null || probabilities.length != VisionConstants.CLASS_COUNT) {
            throw new IllegalArgumentException("probabilities must have length " + VisionConstants.CLASS_COUNT);
        }
        int best = 0;
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > probabilities[best]) best = i;
        }
        return best;
    }

    static boolean isAcceptedClass(int index) {
        return index == 0 || index == 10 || index == 11;
    }
}
