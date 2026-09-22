package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

public final class AntiSpoofingResult {
    public enum Status {
        PENDING,
        LIVE,
        SPOOF,
        ERROR
    }

    private final Status status;
    private final ProbabilityResult result;
    private final String errorMessage;

    private AntiSpoofingResult(Status status, ProbabilityResult result, String errorMessage) {
        this.status = status;
        this.result = result;
        this.errorMessage = errorMessage;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static AntiSpoofingResult pending(ProbabilityResult result) {
        return new AntiSpoofingResult(Status.PENDING, result, null);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static AntiSpoofingResult decision(ProbabilityResult result) {
        return new AntiSpoofingResult(result.isAccepted() ? Status.LIVE : Status.SPOOF, result, null);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static AntiSpoofingResult error(String message) {
        return new AntiSpoofingResult(Status.ERROR, null, message == null ? "Unknown Vision error" : message);
    }

    public Status status() {
        return status;
    }

    /**
     * Returns the running average after an accepted sample, or null before the first sample and on error.
     */
    public ProbabilityResult result() {
        return result;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public boolean isFinished() {
        return status == Status.LIVE || status == Status.SPOOF || status == Status.ERROR;
    }
}
