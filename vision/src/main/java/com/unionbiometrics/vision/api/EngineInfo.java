package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

/** Read-only model-slot metadata returned alongside a loaded engine. */
public final class EngineInfo {
    private final String label;
    private final String backendStatus;
    private final float cropMarginRatio;

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public EngineInfo(String label, String backendStatus, float cropMarginRatio) {
        this.label = label;
        this.backendStatus = backendStatus;
        this.cropMarginRatio = cropMarginRatio;
    }

    public String label() {
        return label;
    }

    public String backendStatus() {
        return backendStatus;
    }

    public float cropMarginRatio() {
        return cropMarginRatio;
    }
}
