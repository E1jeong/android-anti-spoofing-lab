package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

public final class AntiSpoofingResult {
    public enum Status {
        SETTLING, //IRLED를 켠 후에 대기하는 상태, 들어오는 이미지의 안정화를 위해
        COLLECTING, //프레임 수집 하는 상태 (DEFAULT_SAMPLE_COUNT)
        LIVE,
        SPOOF,
        ERROR
    }

    private final Status status;
    private final ProbabilityResult result;
    private final int sampleCount;
    private final int requiredSampleCount;
    private final long samplePreprocessMs;
    private final long sampleInferenceMs;
    private final long settleRemainingMs;
    private final String errorMessage;

    private AntiSpoofingResult(Status status, ProbabilityResult result, int sampleCount,
                         int requiredSampleCount, long samplePreprocessMs, long sampleInferenceMs,
                         long settleRemainingMs, String errorMessage) {
        this.status = status;
        this.result = result;
        this.sampleCount = sampleCount;
        this.requiredSampleCount = requiredSampleCount;
        this.samplePreprocessMs = samplePreprocessMs;
        this.sampleInferenceMs = sampleInferenceMs;
        this.settleRemainingMs = settleRemainingMs;
        this.errorMessage = errorMessage;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static AntiSpoofingResult settling(int requiredSampleCount, long remainingMs) {
        return new AntiSpoofingResult(Status.SETTLING, null, 0, requiredSampleCount,
                0L, 0L, Math.max(0L, remainingMs), null);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static AntiSpoofingResult collecting(ProbabilityResult result, int sampleCount,
                                   int requiredSampleCount, long samplePreprocessMs,
                                   long sampleInferenceMs) {
        return new AntiSpoofingResult(Status.COLLECTING, result, sampleCount,
                requiredSampleCount, samplePreprocessMs, sampleInferenceMs, 0L, null);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static AntiSpoofingResult terminal(ProbabilityResult result, int sampleCount,
                                 long samplePreprocessMs, long sampleInferenceMs) {
        return new AntiSpoofingResult(result.isAccepted() ? Status.LIVE : Status.SPOOF, result,
                sampleCount, sampleCount, samplePreprocessMs, sampleInferenceMs, 0L, null);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static AntiSpoofingResult error(int requiredSampleCount, String message) {
        return new AntiSpoofingResult(Status.ERROR, null, 0, requiredSampleCount,
                0L, 0L, 0L, message == null ? "Unknown Vision error" : message);
    }

    public Status status() {
        return status;
    }

    /** Returns the current average result, or null while settling and on error. */
    public ProbabilityResult result() {
        return result;
    }

    public int sampleCount() {
        return sampleCount;
    }

    public int requiredSampleCount() {
        return requiredSampleCount;
    }

    /** Timing for the most recently accepted sample, not the accumulated session. */
    public long samplePreprocessMs() {
        return samplePreprocessMs;
    }

    /** Timing for the most recently accepted sample, not the accumulated session. */
    public long sampleInferenceMs() {
        return sampleInferenceMs;
    }

    public long settleRemainingMs() {
        return settleRemainingMs;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public boolean isTerminal() {
        return status == Status.LIVE || status == Status.SPOOF || status == Status.ERROR;
    }
}
