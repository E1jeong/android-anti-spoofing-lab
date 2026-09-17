package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

import com.unionbiometrics.vision.VisionSdk;

public final class VisionResult {
    public enum Status {
        SETTLING,
        COLLECTING,
        LIVE,
        SPOOF,
        ERROR
    }

    private final Status status;
    private final float[] probabilities;
    private final int topIndex;
    private final int sampleCount;
    private final int requiredSampleCount;
    private final long preprocessMs;
    private final long inferenceMs;
    private final long settleRemainingMs;
    private final String message;

    private VisionResult(Status status, float[] probabilities, int topIndex, int sampleCount,
                         int requiredSampleCount, long preprocessMs, long inferenceMs,
                         long settleRemainingMs, String message) {
        this.status = status;
        this.probabilities = probabilities == null ? null : probabilities.clone();
        this.topIndex = topIndex;
        this.sampleCount = sampleCount;
        this.requiredSampleCount = requiredSampleCount;
        this.preprocessMs = preprocessMs;
        this.inferenceMs = inferenceMs;
        this.settleRemainingMs = settleRemainingMs;
        this.message = message;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static VisionResult settling(int requiredSampleCount, long remainingMs) {
        return new VisionResult(Status.SETTLING, null, -1, 0, requiredSampleCount,
                0L, 0L, Math.max(0L, remainingMs), null);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static VisionResult collecting(float[] probabilities, int topIndex, int sampleCount,
                                   int requiredSampleCount, long preprocessMs, long inferenceMs) {
        return new VisionResult(Status.COLLECTING, probabilities, topIndex, sampleCount,
                requiredSampleCount, preprocessMs, inferenceMs, 0L, null);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static VisionResult terminal(boolean live, float[] probabilities, int topIndex, int sampleCount,
                                 long preprocessMs, long inferenceMs) {
        return new VisionResult(live ? Status.LIVE : Status.SPOOF, probabilities, topIndex,
                sampleCount, sampleCount, preprocessMs, inferenceMs, 0L, null);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static VisionResult error(int requiredSampleCount, String message) {
        return new VisionResult(Status.ERROR, null, -1, 0, requiredSampleCount,
                0L, 0L, 0L, message == null ? "Unknown Vision error" : message);
    }

    public Status status() {
        return status;
    }

    public float[] probabilities() {
        return probabilities == null ? null : probabilities.clone();
    }

    public int topIndex() {
        return topIndex;
    }

    public String topLabel() {
        return topIndex < 0 ? null : VisionSdk.labels()[topIndex];
    }

    public int sampleCount() {
        return sampleCount;
    }

    public int requiredSampleCount() {
        return requiredSampleCount;
    }

    public long preprocessMs() {
        return preprocessMs;
    }

    public long inferenceMs() {
        return inferenceMs;
    }

    public long settleRemainingMs() {
        return settleRemainingMs;
    }

    public String message() {
        return message;
    }

    public boolean isTerminal() {
        return status == Status.LIVE || status == Status.SPOOF || status == Status.ERROR;
    }
}
